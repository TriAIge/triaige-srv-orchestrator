package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.AiProcessingCompletedRequest;
import br.com.triaige.orchestrator.api.dto.request.PreProcessingCompletedRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.LegalCase;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.*;
import br.com.triaige.orchestrator.infrastructure.ai.AiAnalysisClient;
import br.com.triaige.orchestrator.infrastructure.ai.AiAnalysisRequest;
import br.com.triaige.orchestrator.infrastructure.ai.AiAnalysisResponse;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.s3.DocumentStoragePort;
import br.com.triaige.orchestrator.infrastructure.s3.StoredDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreProcessingCompletedUseCase")
class PreProcessingCompletedUseCaseTest {

    @Mock private TriageSessionRepository sessionRepository;
    @Mock private LegalDocumentRepository documentRepository;
    @Mock private AuditService auditService;
    @Mock private AiAnalysisClient aiAnalysisClient;
    @Mock private DocumentStoragePort documentStoragePort;
    @Mock private AiProcessingCompletedUseCase aiProcessingCompletedUseCase;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PreProcessingCompletedUseCase useCase;

    private UUID sessionId;
    private UUID correlationId;
    private UUID documentId;
    private TriageSession session;
    private LegalDocument document;
    private PreProcessingCompletedRequest request;

    @BeforeEach
    void setUp() throws Exception {
        sessionId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        documentId = UUID.randomUUID();

        LegalCase legalCase = LegalCase.builder()
                .id(UUID.randomUUID())
                .titulo("Caso teste")
                .areaJuridica(LegalArea.TRABALHISTA)
                .tipoCaso(CaseType.RECLAMACAO_TRABALHISTA)
                .build();

        document = LegalDocument.builder()
                .id(documentId)
                .nomeArquivoOriginal("peticao.pdf")
                .tipoDocumento(DocumentType.PETICAO_INICIAL)
                .contentType("application/pdf")
                .rawBucket("triaige-raw-documents")
                .rawObjectKey("tenant/test/raw/peticao.pdf")
                .status(DocumentStatus.AGUARDANDO_PRE_PROCESSAMENTO)
                .build();

        session = TriageSession.builder()
                .id(sessionId)
                .lawFirm(LawFirm.builder().id(UUID.randomUUID()).nome("Escritório Demo").status("ATIVO").build())
                .protocolo("TRI-2026-000001")
                .correlationId(correlationId)
                .status(SessionStatus.AGUARDANDO_PRE_PROCESSAMENTO)
                .documents(new ArrayList<>(List.of(document)))
                .build();
        session.setLegalCase(legalCase);

        // Monta a requisição de callback
        var docInfo = new PreProcessingCompletedRequest.ProcessedDocumentInfo();
        docInfo.setDocumentId(documentId);
        docInfo.setRawBucket("triaige-raw-documents");
        docInfo.setRawObjectKey("tenant/test/raw/peticao.pdf");
        docInfo.setProcessedBucket("triaige-processed-documents");
        docInfo.setProcessedObjectKey("tenant/test/processed/peticao.txt");
        docInfo.setStatus("OK");

        request = new PreProcessingCompletedRequest();
        request.setCorrelationId(correlationId);
        request.setProcessedDocuments(List.of(docInfo));

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    @DisplayName("deve atualizar documentos, mudar status da sessão e chamar a IA via REST")
    void shouldCompletePreProcessingAndCallAiService() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(documentRepository.findBySessionId(sessionId)).thenReturn(List.of(document));
        when(sessionRepository.save(any())).thenReturn(session);

        AiAnalysisResponse.Metadata metadata = new AiAnalysisResponse.Metadata();
        metadata.setToolsUsed(List.of("CASE_SUMMARY", "JURISPRUDENCE_SEARCH"));

        AiAnalysisResponse aiResponse = new AiAnalysisResponse();
        aiResponse.setStatus("COMPLETED");
        aiResponse.setMetadata(metadata);

        when(aiAnalysisClient.analyze(any())).thenReturn(aiResponse);

        StoredDocument storedResult = StoredDocument.builder()
                .bucket("triaige-curated")
                .objectKey("law-firm/" + session.getLawFirm().getId() + "/sessions/" + sessionId + "/curated/result.json")
                .contentType("application/json")
                .sizeBytes(2)
                .build();
        when(documentStoragePort.storeResult(eq(session.getLawFirm().getId()), eq(sessionId), any()))
                .thenReturn(storedResult);

        ArgumentCaptor<AiAnalysisRequest> aiRequestCaptor = ArgumentCaptor.forClass(AiAnalysisRequest.class);
        ArgumentCaptor<AiProcessingCompletedRequest> completedCaptor =
                ArgumentCaptor.forClass(AiProcessingCompletedRequest.class);

        // Act
        useCase.execute(sessionId, request);

        // Assert
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PRE_PROCESSADO);

        verify(aiAnalysisClient).analyze(aiRequestCaptor.capture());
        AiAnalysisRequest aiRequest = aiRequestCaptor.getValue();
        assertThat(aiRequest.getSessionId()).isEqualTo(sessionId.toString());
        assertThat(aiRequest.getCorrelationId()).isEqualTo(correlationId.toString());
        assertThat(aiRequest.getLegalArea()).isEqualTo("TRABALHISTA");
        assertThat(aiRequest.getS3Bucket()).isEqualTo("triaige-processed-documents");
        assertThat(aiRequest.getDocuments()).hasSize(1);
        assertThat(aiRequest.getDocuments().get(0).getS3Key()).isEqualTo("tenant/test/processed/peticao.txt");

        verify(documentStoragePort).storeResult(eq(session.getLawFirm().getId()), eq(sessionId), any());

        verify(aiProcessingCompletedUseCase).execute(eq(sessionId), completedCaptor.capture());
        AiProcessingCompletedRequest completedRequest = completedCaptor.getValue();
        assertThat(completedRequest.getCorrelationId()).isEqualTo(correlationId);
        assertThat(completedRequest.getStatus()).isEqualTo("CONCLUIDO");
        assertThat(completedRequest.getResultBucket()).isEqualTo("triaige-curated");
        assertThat(completedRequest.getResultObjectKey()).isEqualTo(storedResult.getObjectKey());
        assertThat(completedRequest.isJurisprudenceUsed()).isTrue();
    }

    @Test
    @DisplayName("deve completar com FALHA quando a IA não retorna COMPLETED")
    void shouldCompleteWithFailureWhenAiDoesNotComplete() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(documentRepository.findBySessionId(sessionId)).thenReturn(List.of(document));
        when(sessionRepository.save(any())).thenReturn(session);

        AiAnalysisResponse aiResponse = new AiAnalysisResponse();
        aiResponse.setStatus("OUT_OF_SCOPE");
        when(aiAnalysisClient.analyze(any())).thenReturn(aiResponse);

        ArgumentCaptor<AiProcessingCompletedRequest> completedCaptor =
                ArgumentCaptor.forClass(AiProcessingCompletedRequest.class);

        // Act
        useCase.execute(sessionId, request);

        // Assert
        verify(documentStoragePort, never()).storeResult(any(), any(), any());
        verify(aiProcessingCompletedUseCase).execute(eq(sessionId), completedCaptor.capture());
        assertThat(completedCaptor.getValue().getStatus()).isEqualTo("FALHA");
        assertThat(completedCaptor.getValue().getCorrelationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("deve garantir idempotência — callback repetido não chama a IA novamente")
    void shouldBeIdempotentOnDuplicateCallback() {
        // Arrange — sessão já avançada para AGUARDANDO_PROCESSAMENTO_IA
        session.setStatus(SessionStatus.AGUARDANDO_PROCESSAMENTO_IA);
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));

        // Act
        useCase.execute(sessionId, request);
        useCase.execute(sessionId, request);

        // Assert — a IA nunca deve ser chamada
        verify(aiAnalysisClient, never()).analyze(any());
        verify(aiProcessingCompletedUseCase, never()).execute(any(), any());
    }

    @Test
    @DisplayName("deve marcar sessão como FALHA quando documento falha no pré-processamento")
    void shouldMarkSessionAsFailedWhenDocumentFails() {
        // Arrange
        request.getProcessedDocuments().get(0).setStatus("ERROR");
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(documentRepository.findBySessionId(sessionId)).thenReturn(List.of(document));
        when(sessionRepository.save(any())).thenReturn(session);

        // Act
        useCase.execute(sessionId, request);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.FALHA);
        verify(aiAnalysisClient, never()).analyze(any());
        verify(aiProcessingCompletedUseCase, never()).execute(any(), any());
    }
}
