package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.LegalCase;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.*;
import br.com.triaige.orchestrator.domain.exception.BusinessRuleException;
import br.com.triaige.orchestrator.domain.exception.InvalidSessionStateException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.sqs.PreProcessingRequestedMessage;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerProcessingUseCase")
class TriggerProcessingUseCaseTest {

    @Mock private TriageSessionRepository sessionRepository;
    @Mock private LegalDocumentRepository documentRepository;
    @Mock private AuditService auditService;
    @Mock private QueuePublisher queuePublisher;
    @Mock private AwsProperties awsProperties;
    @Mock private AwsProperties.Sqs sqsProperties;

    @InjectMocks
    private TriggerProcessingUseCase useCase;

    private UUID sessionId;
    private TriageSession session;
    private LegalDocument document;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();

        LegalCase legalCase = LegalCase.builder()
                .id(UUID.randomUUID())
                .titulo("Caso teste")
                .areaJuridica(LegalArea.TRABALHISTA)
                .tipoCaso(CaseType.RECLAMACAO_TRABALHISTA)
                .build();

        document = LegalDocument.builder()
                .id(UUID.randomUUID())
                .nomeArquivoOriginal("peticao.pdf")
                .tipoDocumento(DocumentType.PETICAO_INICIAL)
                .contentType("application/pdf")
                .rawBucket("triaige-raw-documents")
                .rawObjectKey("tenant/test/raw/peticao.pdf")
                .status(DocumentStatus.REGISTRADO)
                .build();

        session = TriageSession.builder()
                .id(sessionId)
                .lawFirm(LawFirm.builder().id(UUID.randomUUID()).nome("Escritório Demo").status("ATIVO").build())
                .protocolo("TRI-2026-000001")
                .correlationId(UUID.randomUUID())
                .status(SessionStatus.ABERTA)
                .documents(new ArrayList<>(List.of(document)))
                .build();
        session.setLegalCase(legalCase);

        lenient().when(awsProperties.getSqs()).thenReturn(sqsProperties);
        lenient().when(sqsProperties.getDocsPreprocessingQueueUrl())
                .thenReturn("http://localhost:4566/000000000000/triaige-docs-preprocessing");
    }

    @Test
    @DisplayName("deve disparar processamento e publicar mensagem SQS com ponteiros")
    void shouldTriggerProcessingAndPublishSqsMessage() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(documentRepository.findBySessionId(sessionId)).thenReturn(List.of(document));
        when(sessionRepository.save(any())).thenReturn(session);

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);

        // Act
        useCase.execute(sessionId, null);

        // Assert
        verify(queuePublisher).publish(anyString(), messageCaptor.capture());
        PreProcessingRequestedMessage message = (PreProcessingRequestedMessage) messageCaptor.getValue();

        assertThat(message.getEventType()).isEqualTo("PRE_PROCESSING_REQUESTED");
        assertThat(message.getSessionId()).isEqualTo(sessionId);
        assertThat(message.getDocuments()).hasSize(1);
        assertThat(message.getDocuments().get(0).getBucket()).isEqualTo("triaige-raw-documents");
        // NÃO deve conter o conteúdo do arquivo
        assertThat(message.getDocuments().get(0).getObjectKey()).doesNotContain("content");
    }

    @Test
    @DisplayName("deve alterar status da sessão para AGUARDANDO_PRE_PROCESSAMENTO")
    void shouldUpdateSessionStatus() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(documentRepository.findBySessionId(sessionId)).thenReturn(List.of(document));
        ArgumentCaptor<TriageSession> sessionCaptor = ArgumentCaptor.forClass(TriageSession.class);
        when(sessionRepository.save(sessionCaptor.capture())).thenReturn(session);

        // Act
        useCase.execute(sessionId, null);

        // Assert
        assertThat(sessionCaptor.getValue().getStatus())
                .isEqualTo(SessionStatus.AGUARDANDO_PRE_PROCESSAMENTO);
    }

    @Test
    @DisplayName("não deve processar sessão sem documentos registrados")
    void shouldRejectSessionWithoutDocuments() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(documentRepository.findBySessionId(sessionId)).thenReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(sessionId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("sem documentos");
        verify(queuePublisher, never()).publish(any(), any());
    }

    @Test
    @DisplayName("não deve processar sessão cancelada")
    void shouldRejectCancelledSession() {
        // Arrange
        session.setStatus(SessionStatus.CANCELADA);
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(sessionId, null))
                .isInstanceOf(InvalidSessionStateException.class);
        verify(queuePublisher, never()).publish(any(), any());
    }
}
