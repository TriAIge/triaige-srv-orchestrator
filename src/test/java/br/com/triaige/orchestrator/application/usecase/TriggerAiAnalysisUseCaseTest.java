package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.event.AiAnalysisTriggeredEvent;
import br.com.triaige.orchestrator.domain.exception.McpAiHttpException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.http.McpAiAnalysisClient;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisResponseDto;
import br.com.triaige.orchestrator.infrastructure.persistence.AiToolCallRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.LawFirmRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageResultRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.render.TriageReportRenderer;
import br.com.triaige.orchestrator.infrastructure.s3.S3DocumentStorageService;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import br.com.triaige.orchestrator.shared.metrics.AiAnalysisMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre o coração do fluxo assíncrono: caminho feliz, os 2 branches de falha
 * (rede esgotada após retry, erro HTTP explícito sem retry) e a proteção de idempotência
 * via {@code DataIntegrityViolationException}. {@link TransactionTemplate} é mockado para simplesmente invocar o
 * callback recebido — não há transação JDBC real neste teste.
 */
class TriggerAiAnalysisUseCaseTest {

    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID LAW_FIRM_ID = UUID.randomUUID();

    private TriageSessionRepository sessionRepository;
    private LawFirmRepository lawFirmRepository;
    private AuditService auditService;
    private McpAiAnalysisClient mcpAiAnalysisClient;
    private TriageReportRenderer renderer;
    private S3DocumentStorageService s3StorageService;
    private TriageResultRepository triageResultRepository;
    private AiToolCallRepository aiToolCallRepository;
    private QueuePublisher queuePublisher;
    private AwsProperties awsProperties;
    private AiAnalysisMetrics metrics;
    private TriggerAiAnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(TriageSessionRepository.class);
        lawFirmRepository = mock(LawFirmRepository.class);
        auditService = mock(AuditService.class);
        mcpAiAnalysisClient = mock(McpAiAnalysisClient.class);
        renderer = mock(TriageReportRenderer.class);
        s3StorageService = mock(S3DocumentStorageService.class);
        triageResultRepository = mock(TriageResultRepository.class);
        aiToolCallRepository = mock(AiToolCallRepository.class);
        queuePublisher = mock(QueuePublisher.class);
        metrics = mock(AiAnalysisMetrics.class);

        awsProperties = new AwsProperties();
        awsProperties.getS3().setCuratedDocumentsBucket("bucket-curated");
        awsProperties.getSqs().setResultsReadyQueueUrl("http://queue/results-ready");

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class, RETURNS_DEFAULTS);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        when(lawFirmRepository.findById(any())).thenReturn(Optional.empty());
        when(sessionRepository.findById(SESSION_ID)).thenAnswer(inv -> Optional.of(
                TriageSession.builder().id(SESSION_ID).status(SessionStatus.COMPLETED).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(renderer.render(any(), any())).thenReturn("# markdown");

        useCase = new TriggerAiAnalysisUseCase(sessionRepository, lawFirmRepository, auditService,
                mcpAiAnalysisClient, renderer, s3StorageService, triageResultRepository, aiToolCallRepository,
                queuePublisher, awsProperties, metrics, new ObjectMapper(), transactionTemplate);
    }

    private AiAnalysisTriggeredEvent buildEvent() {
        return new AiAnalysisTriggeredEvent(SESSION_ID, LAW_FIRM_ID, UUID.randomUUID(), "PROTO-001",
                UUID.randomUUID(), "civel", "cobranca", "Caso teste",
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), List.of(), List.of());
    }

    private AnalysisResponseDto buildResponse() {
        return AnalysisResponseDto.builder()
                .sessionId(SESSION_ID)
                .status("COMPLETED")
                .relatorioEstruturado(AnalysisResponseDto.RelatorioEstruturadoDto.builder()
                        .schemaVersion("2.0")
                        .build())
                .build();
    }

    @Test
    void happyPath_marksAnalysisCompletedAndPublishesToQ3() {
        when(mcpAiAnalysisClient.analyze(any(), eq(SESSION_ID))).thenReturn(buildResponse());
        when(triageResultRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(buildEvent());

        verify(sessionRepository, times(1)).save(argThatStatus(SessionStatus.ANALYZING));
        verify(sessionRepository, times(1)).save(argThatStatus(SessionStatus.ANALYSIS_COMPLETED));
        verify(queuePublisher, times(1)).publish(eq("http://queue/results-ready"), any());
        verify(metrics, times(1)).incrementSuccess();
        verify(sessionRepository, never()).save(argThatStatus(SessionStatus.ANALYSIS_FAILED));
    }

    @Test
    void sessionNotEligible_skipsWithoutCallingMcpAi() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(
                TriageSession.builder().id(SESSION_ID).status(SessionStatus.ANALYZING).build()));

        useCase.execute(buildEvent());

        verify(mcpAiAnalysisClient, never()).analyze(any(), any());
        verify(queuePublisher, never()).publish(anyString(), any());
    }

    @Test
    void networkFailureExhausted_marksAnalysisFailedWithRetry() {
        when(mcpAiAnalysisClient.analyze(any(), eq(SESSION_ID)))
                .thenThrow(new ResourceAccessException("connection refused"))
                .thenThrow(new ResourceAccessException("connection refused"));

        useCase.execute(buildEvent());

        verify(mcpAiAnalysisClient, times(2)).analyze(any(), eq(SESSION_ID));
        verify(metrics, times(1)).incrementRetry();
        verify(metrics, times(1)).incrementFailure("MCP_AI_UNAVAILABLE");
        verify(sessionRepository, times(1)).save(argThatStatus(SessionStatus.ANALYSIS_FAILED));
        verify(queuePublisher, never()).publish(anyString(), any());
    }

    @Test
    void explicitHttpError_marksAnalysisFailedWithoutRetry() {
        when(mcpAiAnalysisClient.analyze(any(), eq(SESSION_ID)))
                .thenThrow(new McpAiHttpException(HttpStatusCode.valueOf(502), "bad gateway"));

        useCase.execute(buildEvent());

        verify(mcpAiAnalysisClient, times(1)).analyze(any(), eq(SESSION_ID));
        verify(metrics, never()).incrementRetry();
        verify(metrics, times(1)).incrementFailure("MCP_AI_HTTP_ERROR");
        verify(sessionRepository, times(1)).save(argThatStatus(SessionStatus.ANALYSIS_FAILED));
    }

    @Test
    void duplicateTriageResult_doesNotOverwriteWinnerStatusOrRepublish() {
        when(mcpAiAnalysisClient.analyze(any(), eq(SESSION_ID))).thenReturn(buildResponse());
        when(triageResultRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        useCase.execute(buildEvent());

        verify(sessionRepository, never()).save(argThatStatus(SessionStatus.ANALYSIS_COMPLETED));
        verify(sessionRepository, never()).save(argThatStatus(SessionStatus.ANALYSIS_FAILED));
        verify(queuePublisher, never()).publish(anyString(), any());
        verify(metrics, never()).incrementSuccess();
    }

    private TriageSession argThatStatus(SessionStatus status) {
        return org.mockito.ArgumentMatchers.argThat(s -> s != null && s.getStatus() == status);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
