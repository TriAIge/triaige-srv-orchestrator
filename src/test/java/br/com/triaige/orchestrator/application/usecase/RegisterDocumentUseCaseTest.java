package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.RegisterDocumentResponse;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.DocumentType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.InvalidSessionStateException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.s3.DocumentStoragePort;
import br.com.triaige.orchestrator.infrastructure.s3.StoredDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegisterDocumentUseCase")
class RegisterDocumentUseCaseTest {

    @Mock private TriageSessionRepository sessionRepository;
    @Mock private LegalDocumentRepository documentRepository;
    @Mock private AuditService auditService;
    @Mock private DocumentStoragePort documentStoragePort;

    @InjectMocks
    private RegisterDocumentUseCase useCase;

    private UUID sessionId;
    private TriageSession openSession;
    private MultipartFile file;
    private StoredDocument storedDocument;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();

        LawFirm lawFirm = LawFirm.builder().id(UUID.randomUUID()).nome("Escritório Demo").status("ATIVO").build();

        openSession = TriageSession.builder()
                .id(sessionId)
                .lawFirm(lawFirm)
                .protocolo("TRI-2026-000001")
                .correlationId(UUID.randomUUID())
                .status(SessionStatus.ABERTA)
                .build();

        file = new MockMultipartFile("file", "peticao-inicial.pdf", "application/pdf", "conteudo".getBytes());

        storedDocument = StoredDocument.builder()
                .bucket("triaige-raw-documents")
                .objectKey("law-firm/" + lawFirm.getId() + "/sessions/" + sessionId + "/raw/peticao-inicial.pdf")
                .contentType("application/pdf")
                .sizeBytes(file.getSize())
                .build();
    }

    @Test
    @DisplayName("deve registrar documento com sucesso em sessão aberta")
    void shouldRegisterDocumentSuccessfully() {
        // Arrange
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));
        when(documentStoragePort.upload(eq(openSession.getLawFirm().getId()), eq(sessionId), any()))
                .thenReturn(storedDocument);
        when(documentRepository.save(any(LegalDocument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        RegisterDocumentResponse response = useCase.execute(
                sessionId, file, DocumentType.PETICAO_INICIAL, null, UUID.randomUUID());

        // Assert
        assertThat(response.getDocumentId()).isNotNull();
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.REGISTRADO);
        assertThat(response.getBucket()).isEqualTo("triaige-raw-documents");
        verify(documentStoragePort).upload(eq(openSession.getLawFirm().getId()), eq(sessionId), any());
        verify(documentRepository).save(any(LegalDocument.class));
    }

    @Test
    @DisplayName("deve lançar SessionNotFoundException quando sessão não existe")
    void shouldThrowWhenSessionNotFound() {
        // Arrange
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(sessionId, file, DocumentType.PETICAO_INICIAL, null, UUID.randomUUID()))
                .isInstanceOf(SessionNotFoundException.class);
        verify(documentStoragePort, never()).upload(any(), any(), any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("não deve registrar documento em sessão concluída")
    void shouldNotRegisterDocumentInConcludedSession() {
        // Arrange
        openSession.setStatus(SessionStatus.CONCLUIDA);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(sessionId, file, DocumentType.PETICAO_INICIAL, null, UUID.randomUUID()))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("registro de documento");
        verify(documentStoragePort, never()).upload(any(), any(), any());
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("não deve registrar documento em sessão aguardando processamento")
    void shouldNotRegisterDocumentWhenProcessingStarted() {
        // Arrange
        openSession.setStatus(SessionStatus.AGUARDANDO_PRE_PROCESSAMENTO);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(openSession));

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(sessionId, file, DocumentType.PETICAO_INICIAL, null, UUID.randomUUID()))
                .isInstanceOf(InvalidSessionStateException.class);
    }
}
