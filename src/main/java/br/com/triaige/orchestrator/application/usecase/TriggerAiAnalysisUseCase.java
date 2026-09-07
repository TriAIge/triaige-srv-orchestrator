package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.McpResultCallbackRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.AiToolCall;
import br.com.triaige.orchestrator.domain.entity.LawFirm;
import br.com.triaige.orchestrator.domain.entity.TriageResult;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.AiToolCallStatus;
import br.com.triaige.orchestrator.domain.enums.AiToolName;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.event.AiAnalysisTriggeredEvent;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.domain.exception.TemplateRenderException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.http.McpAiAnalysisClient;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisRequestDto;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisResponseDto;
import br.com.triaige.orchestrator.infrastructure.persistence.AiToolCallRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.LawFirmRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageResultRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.render.TriageReportRenderContext;
import br.com.triaige.orchestrator.infrastructure.render.TriageReportRenderer;
import br.com.triaige.orchestrator.infrastructure.s3.S3DocumentStorageService;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import br.com.triaige.orchestrator.infrastructure.sqs.message.ResultsReadyMessage;
import br.com.triaige.orchestrator.shared.metrics.AiAnalysisMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Coração do fluxo da Fase 4 (spec seção 4, passos 3-11): monta o {@code AnalysisRequest},
 * chama o mcp-ai, renderiza o relatório no template canônico, grava JSON+Markdown no S3
 * curated, persiste {@code triage_results} e publica em Q3 — ou, em qualquer falha, marca
 * {@code ANALYSIS_FAILED} e audita o código de erro.
 *
 * <p><b>Não é {@code @Transactional}</b>: orquestra várias transações curtas
 * ({@link #markAnalyzing}, {@link #persistResult}, {@link #handleFailure}, via
 * {@link TransactionTemplate} — {@code @Transactional} não funcionaria aqui por
 * auto-invocação) intercaladas com I/O externo que não deve segurar uma conexão JDBC
 * (chamada HTTP de até ~130s, 2 PutObject no S3, publish no SQS). O
 * {@code try/catch} em {@link #execute} é a rede de segurança que garante que qualquer
 * falha intermediária — mesmo depois de a chamada ao mcp-ai já ter tido sucesso — ainda
 * resulte em {@code ANALYSIS_FAILED}, nunca numa sessão presa em {@code ANALYZING}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerAiAnalysisUseCase {

    /** ai_tool_calls.provider do registro-stub — espelha AiToolProvider.MOCKAPI_IO do mcp-ai (fonte real da chamada). */
    private static final String JURISPRUDENCE_PROVIDER = "mockapi_io";
    private static final long NETWORK_RETRY_DELAY_MS = 5000;

    private final TriageSessionRepository sessionRepository;
    private final LawFirmRepository lawFirmRepository;
    private final AuditService auditService;
    private final McpAiAnalysisClient mcpAiAnalysisClient;
    private final TriageReportRenderer renderer;
    private final S3DocumentStorageService s3StorageService;
    private final TriageResultRepository triageResultRepository;
    private final AiToolCallRepository aiToolCallRepository;
    private final QueuePublisher queuePublisher;
    private final AwsProperties awsProperties;
    private final AiAnalysisMetrics metrics;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public void execute(AiAnalysisTriggeredEvent event) {
        try {
            if (!markAnalyzing(event)) {
                return;
            }
            metrics.recordTriggerLatency(java.time.Duration.between(event.triggeredAt(), LocalDateTime.now()).toMillis());

            AnalysisResponseDto response = callMcpAi(event);
            RenderedReport rendered = renderAndHash(event, response);
            writeToS3(event, rendered);
            if (persistResult(event, response, rendered)) {
                publishResultsReady(event, response);
                metrics.incrementSuccess();
            }
        } catch (Exception e) {
            String errorCode = errorCodeFor(e);
            log.error("AI analysis failed: sessionId={}, errorCode={}", event.sessionId(), errorCode, e);
            handleFailure(event, errorCode);
            metrics.incrementFailure(errorCode);
        }
    }

    /** Idempotência preventiva (spec Fase 4, plano de implementação, seção "Riscos", item 3). */
    private boolean markAnalyzing(AiAnalysisTriggeredEvent event) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            TriageSession session = sessionRepository.findById(event.sessionId())
                    .orElseThrow(() -> new SessionNotFoundException(event.sessionId()));
            if (session.getStatus() != SessionStatus.COMPLETED && session.getStatus() != SessionStatus.PARTIALLY_COMPLETED) {
                log.warn("AI analysis skipped, session not eligible anymore (possível evento duplicado): sessionId={}, status={}",
                        event.sessionId(), session.getStatus());
                return false;
            }
            session.setStatus(SessionStatus.ANALYZING);
            sessionRepository.save(session);
            auditService.record(event.sessionId(), event.lawFirmId(), event.correlationId(),
                    EventType.AI_ANALYSIS_STARTED, "Disparando análise de IA (triaige-srv-mcp-ai)");
            return true;
        }));
    }

    /** 1 retry com backoff fixo de 5s, só para falha de rede/conexão (spec seção 2.4). */
    private AnalysisResponseDto callMcpAi(AiAnalysisTriggeredEvent event) {
        AnalysisRequestDto request = buildAnalysisRequest(event);
        try {
            return mcpAiAnalysisClient.analyze(request, event.sessionId());
        } catch (ResourceAccessException e) {
            log.warn("Falha de rede ao chamar mcp-ai, tentando 1 retry: sessionId={}", event.sessionId());
            metrics.incrementRetry();
            sleep(NETWORK_RETRY_DELAY_MS);
            return mcpAiAnalysisClient.analyze(request, event.sessionId());
        }
    }

    private AnalysisRequestDto buildAnalysisRequest(AiAnalysisTriggeredEvent event) {
        List<McpResultCallbackRequest.ProcessedGroup> processedGroups = event.processedGroups();
        List<McpResultCallbackRequest.FailedDocument> failedDocuments = event.failedDocuments();

        return AnalysisRequestDto.builder()
                .sessionId(event.sessionId())
                .correlationId(event.correlationId())
                .protocolo(event.protocolo())
                .legalCase(AnalysisRequestDto.LegalCaseDto.builder()
                        .id(event.legalCaseId())
                        .titulo(event.titulo())
                        .areaJuridica(event.areaJuridica())
                        .tipoCaso(event.tipoCaso())
                        .build())
                .processedGroups(processedGroups == null ? List.of() : processedGroups.stream()
                        .map(g -> AnalysisRequestDto.ProcessedGroupDto.builder()
                                .attachmentGroupId(g.getAttachmentGroupId())
                                .trustedBucket(g.getTrustedBucket())
                                .trustedObjectKey(g.getTrustedObjectKey())
                                .tipoDocumento(g.getTipoDocumento())
                                .resumido(g.isResumido())
                                .build())
                        .toList())
                .failedDocuments(failedDocuments == null ? List.of() : failedDocuments.stream()
                        .map(d -> AnalysisRequestDto.FailedDocumentDto.builder()
                                .documentId(d.getDocumentId())
                                .errorMessage(d.getErrorMessage())
                                .build())
                        .toList())
                .build();
    }

    private RenderedReport renderAndHash(AiAnalysisTriggeredEvent event, AnalysisResponseDto response) {
        try {
            String jsonText = objectMapper.writeValueAsString(response.getRelatorioEstruturado());
            String hash = sha256Hex(jsonText);
            String nomeEscritorio = lawFirmRepository.findById(event.lawFirmId()).map(LawFirm::getNome).orElse(null);

            LocalDateTime dataHoraProcessamento = response.getRelatorioEstruturado().getMetadados() != null
                    ? response.getRelatorioEstruturado().getMetadados().getGeradoEm()
                    : LocalDateTime.now();

            TriageReportRenderContext context = new TriageReportRenderContext(
                    event.protocolo(), nomeEscritorio, event.lawFirmId(),
                    event.sessionCreatedAt(), dataHoraProcessamento,
                    SessionStatus.ANALYSIS_COMPLETED.name(), hash);

            String markdown = renderer.render(response.getRelatorioEstruturado(), context);
            return new RenderedReport(jsonText, markdown);
        } catch (TemplateRenderException e) {
            metrics.incrementRenderFailure();
            throw e;
        } catch (Exception e) {
            metrics.incrementRenderFailure();
            throw new TemplateRenderException("Falha ao preparar a renderização do relatório: " + e.getMessage(), e);
        }
    }

    private void writeToS3(AiAnalysisTriggeredEvent event, RenderedReport rendered) {
        String bucket = awsProperties.getS3().getCuratedDocumentsBucket();
        s3StorageService.putObject(bucket, curatedKey(event, "json"),
                rendered.json().getBytes(StandardCharsets.UTF_8), "application/json");
        s3StorageService.putObject(bucket, curatedKey(event, "md"),
                rendered.markdown().getBytes(StandardCharsets.UTF_8), "text/markdown");
    }

    /**
     * {@code UNIQUE KEY uk_triage_results_session_id}: uma segunda tentativa falha
     * explicitamente, nunca sobrescreve. Retorna {@code false} para a execução "perdedora"
     * — o chamador não deve publicar em Q3 nem contar sucesso de novo: a execução vencedora
     * já cuidou disso.
     */
    private boolean persistResult(AiAnalysisTriggeredEvent event, AnalysisResponseDto response, RenderedReport rendered) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            UUID jurisprudenceCallId = response.getJurisprudenceCallId();
            if (jurisprudenceCallId != null) {
                ensureJurisprudenceCallStub(event.sessionId(), jurisprudenceCallId);
            }

            TriageResult result = TriageResult.builder()
                    .sessionId(event.sessionId())
                    .resultBucket(awsProperties.getS3().getCuratedDocumentsBucket())
                    .resultObjectKey(curatedKey(event, "json"))
                    .summaryObjectKey(curatedKey(event, "md"))
                    .jurisprudenceCallId(jurisprudenceCallId)
                    .build();

            try {
                triageResultRepository.saveAndFlush(result);
            } catch (DataIntegrityViolationException e) {
                log.warn("triage_results já existe para sessionId={} (execução duplicada) — não sobrescrevendo status",
                        event.sessionId());
                return false;
            }

            TriageSession session = sessionRepository.findById(event.sessionId())
                    .orElseThrow(() -> new SessionNotFoundException(event.sessionId()));
            session.setStatus(SessionStatus.ANALYSIS_COMPLETED);
            sessionRepository.save(session);
            auditService.record(event.sessionId(), event.lawFirmId(), event.correlationId(),
                    EventType.AI_ANALYSIS_COMPLETED, "Análise de IA concluída com sucesso");
            return true;
        }));
    }

    /**
     * Satisfaz {@code fk_triage_results_jurisprudence_call} local: o UUID de
     * {@code jurisprudenceCallId} foi gerado na tabela {@code ai_tool_calls} do mcp-ai — um
     * banco distinto — não na do Orchestrator. Sem este stub, o INSERT em
     * {@code triage_results} violaria a FK local sempre que houvesse jurisprudência citada.
     */
    private void ensureJurisprudenceCallStub(UUID sessionId, UUID jurisprudenceCallId) {
        if (aiToolCallRepository.existsById(jurisprudenceCallId)) {
            return;
        }
        AiToolCall stub = AiToolCall.builder()
                .id(jurisprudenceCallId)
                .sessionId(sessionId)
                .toolName(AiToolName.JURISPRUDENCE_QUERY)
                .provider(JURISPRUDENCE_PROVIDER)
                .status(AiToolCallStatus.SUCCESS)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build();
        aiToolCallRepository.save(stub);
    }

    /** Falha aqui é logada + métrica, não reverte ANALYSIS_COMPLETED já commitado (gap conhecido, fora da spec). */
    private void publishResultsReady(AiAnalysisTriggeredEvent event, AnalysisResponseDto response) {
        ResultsReadyMessage message = ResultsReadyMessage.builder()
                .sessionId(event.sessionId())
                .correlationId(event.correlationId())
                .protocolo(event.protocolo())
                .lawFirmId(event.lawFirmId())
                .resultBucket(awsProperties.getS3().getCuratedDocumentsBucket())
                .resultObjectKey(curatedKey(event, "json"))
                .summaryObjectKey(curatedKey(event, "md"))
                .build();
        queuePublisher.publish(awsProperties.getSqs().getResultsReadyQueueUrl(), message);
    }

    private void handleFailure(AiAnalysisTriggeredEvent event, String errorCode) {
        transactionTemplate.executeWithoutResult(status -> {
            sessionRepository.findById(event.sessionId()).ifPresent(session -> {
                session.setStatus(SessionStatus.ANALYSIS_FAILED);
                sessionRepository.save(session);
            });
            auditService.record(event.sessionId(), event.lawFirmId(), event.correlationId(),
                    EventType.AI_ANALYSIS_FAILED, "Falha na análise de IA: errorCode=" + errorCode);
        });
    }

    private String errorCodeFor(Exception e) {
        if (e instanceof ResourceAccessException) {
            return "MCP_AI_UNAVAILABLE";
        }
        if (e instanceof br.com.triaige.orchestrator.domain.exception.McpAiHttpException) {
            return "MCP_AI_HTTP_ERROR";
        }
        if (e instanceof TemplateRenderException) {
            return "RENDER_FAILED";
        }
        if (e instanceof br.com.triaige.orchestrator.domain.exception.DocumentStorageException) {
            return "S3_WRITE_FAILED";
        }
        return "UNEXPECTED";
    }

    private String curatedKey(AiAnalysisTriggeredEvent event, String extension) {
        return "%s/%s/relatorio.%s".formatted(event.lawFirmId(), event.sessionId(), extension);
    }

    private String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RenderedReport(String json, String markdown) {
    }
}
