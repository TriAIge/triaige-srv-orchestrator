package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.AuditEventResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.domain.entity.AuditEvent;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.LegalCase;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.*;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.AuditEventRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetSessionStatusUseCase")
class GetSessionStatusUseCaseTest {

    @Mock private TriageSessionRepository sessionRepository;
    @Mock private AuditEventRepository auditEventRepository;

    @InjectMocks
    private GetSessionStatusUseCase useCase;

    private UUID sessionId;
    private TriageSession session;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();

        LegalCase legalCase = LegalCase.builder()
                .id(UUID.randomUUID())
                .titulo("Reclamação trabalhista")
                .areaJuridica(LegalArea.TRABALHISTA)
                .tipoCaso(CaseType.RECLAMACAO_TRABALHISTA)
                .build();

        LegalDocument doc = LegalDocument.builder()
                .id(UUID.randomUUID())
                .nomeArquivoOriginal("peticao.pdf")
                .tipoDocumento(DocumentType.PETICAO_INICIAL)
                .status(DocumentStatus.PRE_PROCESSADO)
                .build();

        session = TriageSession.builder()
                .id(sessionId)
                .lawFirm(LawFirm.builder().id(UUID.randomUUID()).nome("Escritório Demo").status("ATIVO").build())
                .protocolo("TRI-2026-000001")
                .correlationId(UUID.randomUUID())
                .status(SessionStatus.CONCLUIDA)
                .documents(new ArrayList<>(List.of(doc)))
                .build();
        session.setLegalCase(legalCase);
    }

    @Test
    @DisplayName("deve retornar detalhes completos da sessão")
    void shouldReturnSessionDetails() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));

        // Act
        SessionDetailResponse response = useCase.getSession(sessionId, null);

        // Assert
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getProtocolo()).isEqualTo("TRI-2026-000001");
        assertThat(response.getStatus()).isEqualTo(SessionStatus.CONCLUIDA);
        assertThat(response.getAreaJuridica()).isEqualTo(LegalArea.TRABALHISTA);
        assertThat(response.getDocuments()).hasSize(1);
        assertThat(response.getDocuments().get(0).getNomeArquivoOriginal()).isEqualTo("peticao.pdf");
    }

    @Test
    @DisplayName("deve lançar SessionNotFoundException para sessão inexistente")
    void shouldThrowWhenSessionNotFound() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> useCase.getSession(sessionId, null))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("deve retornar eventos de auditoria em ordem cronológica")
    void shouldReturnAuditEventsInOrder() {
        // Arrange
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        List<AuditEvent> events = List.of(
                buildEvent(EventType.SESSION_CREATED, "Sessão criada",
                        LocalDateTime.of(2026, 6, 10, 18, 0, 0)),
                buildEvent(EventType.DOCUMENT_REGISTERED, "Documento registrado",
                        LocalDateTime.of(2026, 6, 10, 18, 1, 0)),
                buildEvent(EventType.PROCESSING_REQUESTED, "Processamento disparado",
                        LocalDateTime.of(2026, 6, 10, 18, 2, 0))
        );
        when(auditEventRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).thenReturn(events);

        // Act
        List<AuditEventResponse> result = useCase.getEvents(sessionId, null);

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getEventType()).isEqualTo(EventType.SESSION_CREATED);
        assertThat(result.get(1).getEventType()).isEqualTo(EventType.DOCUMENT_REGISTERED);
        assertThat(result.get(2).getEventType()).isEqualTo(EventType.PROCESSING_REQUESTED);
    }

    @Test
    @DisplayName("deve isolar por escritório jurídico — não deve retornar sessão de outro escritório")
    void shouldIsolateByLawFirm() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));

        // Act & Assert — outro escritório não deve enxergar esta sessão
        assertThatThrownBy(() -> useCase.getSession(sessionId, UUID.randomUUID()))
                .isInstanceOf(SessionNotFoundException.class);
    }

    private AuditEvent buildEvent(EventType type, String description, LocalDateTime createdAt) {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .correlationId(UUID.randomUUID())
                .eventType(type)
                .description(description)
                .build();
        // Simula @CreationTimestamp
        try {
            var field = AuditEvent.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(event, createdAt);
        } catch (Exception ignored) {}
        return event;
    }
}
