package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.AiProcessingCompletedRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.LegalCase;
import br.com.triaige.orchestrator.domain.entity.NotificationRecipient;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.*;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageResultRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import br.com.triaige.orchestrator.infrastructure.sqs.TriageResultReadyMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiProcessingCompletedUseCase")
class AiProcessingCompletedUseCaseTest {

    @Mock private TriageSessionRepository sessionRepository;
    @Mock private TriageResultRepository resultRepository;
    @Mock private AuditService auditService;
    @Mock private QueuePublisher queuePublisher;
    @Mock private AwsProperties awsProperties;
    @Mock private AwsProperties.Sqs sqsProperties;

    @InjectMocks
    private AiProcessingCompletedUseCase useCase;

    private UUID sessionId;
    private UUID correlationId;
    private TriageSession session;
    private AiProcessingCompletedRequest request;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        correlationId = UUID.randomUUID();

        LegalCase legalCase = LegalCase.builder()
                .id(UUID.randomUUID())
                .titulo("Caso teste")
                .areaJuridica(LegalArea.TRABALHISTA)
                .tipoCaso(CaseType.RECLAMACAO_TRABALHISTA)
                .build();

        NotificationRecipient recipient = NotificationRecipient.builder()
                .id(UUID.randomUUID())
                .nome("João Silva")
                .email("joao@email.com")
                .telefone("+5511999999999")
                .canalPreferencial(NotificationChannel.EMAIL)
                .build();

        session = TriageSession.builder()
                .id(sessionId)
                .lawFirm(LawFirm.builder().id(UUID.randomUUID()).nome("Escritório Demo").status("ATIVO").build())
                .protocolo("TRI-2026-000001")
                .correlationId(correlationId)
                .status(SessionStatus.AGUARDANDO_PROCESSAMENTO_IA)
                .build();
        session.setLegalCase(legalCase);
        session.setRecipient(recipient);

        request = new AiProcessingCompletedRequest();
        request.setCorrelationId(correlationId);
        request.setStatus("CONCLUIDO");
        request.setResultBucket("triaige-results");
        request.setResultObjectKey("tenant/escritorio-001/sessions/" + sessionId + "/result/resultado.json");
        request.setSummaryObjectKey("tenant/escritorio-001/sessions/" + sessionId + "/result/resumo.txt");
        request.setJurisprudenceUsed(true);

        lenient().when(awsProperties.getSqs()).thenReturn(sqsProperties);
        lenient().when(sqsProperties.getResultsReadyQueueUrl())
                .thenReturn("http://localhost:4566/000000000000/triaige-results-ready");
    }

    @Test
    @DisplayName("deve concluir sessão, salvar resultado e publicar notificação")
    void shouldCompleteSessionAndPublishNotification() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenReturn(session);

        ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);

        // Act
        useCase.execute(sessionId, request);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CONCLUIDA);

        verify(resultRepository).save(any());
        verify(queuePublisher).publish(anyString(), msgCaptor.capture());

        TriageResultReadyMessage message = (TriageResultReadyMessage) msgCaptor.getValue();
        assertThat(message.getEventType()).isEqualTo("TRIAGE_RESULT_READY");
        assertThat(message.getSessionId()).isEqualTo(sessionId);
        assertThat(message.getResultBucket()).isEqualTo("triaige-results");
        assertThat(message.getRecipient().getEmail()).isEqualTo("joao@email.com");
        assertThat(message.getRecipient().getPreferredChannel()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("deve garantir idempotência — callback repetido não duplica notificação")
    void shouldBeIdempotentOnDuplicateCallback() {
        // Arrange — sessão já CONCLUIDA
        session.setStatus(SessionStatus.CONCLUIDA);
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));

        // Act
        useCase.execute(sessionId, request);
        useCase.execute(sessionId, request);

        // Assert — a notificação nunca deve ser publicada
        verify(queuePublisher, never()).publish(any(), any());
        verify(resultRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve marcar sessão como FALHA quando IA reporta falha")
    void shouldMarkSessionAsFailedWhenAiReportsFail() {
        // Arrange
        request.setStatus("FALHA");
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        // Act
        useCase.execute(sessionId, request);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SessionStatus.FALHA);
        verify(queuePublisher, never()).publish(any(), any());
        verify(resultRepository, never()).save(any());
    }

    @Test
    @DisplayName("deve registrar evento de auditoria AI_PROCESSING_COMPLETED")
    void shouldRecordAuditEvent() {
        // Arrange
        when(sessionRepository.findByIdWithDetails(sessionId)).thenReturn(Optional.of(session));
        when(resultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenReturn(session);

        // Act
        useCase.execute(sessionId, request);

        // Assert
        verify(auditService).record(
                eq(sessionId),
                eq(correlationId),
                eq(EventType.AI_PROCESSING_COMPLETED),
                anyString()
        );
    }
}
