package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.McpResultCallbackRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LegalCase;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.event.AiAnalysisTriggeredEvent;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.domain.exception.ValidationException;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * POST {basePath}/sessions/{sessionId}/mcp-result — spec da Fase 2, seção 6. O
 * triaige-srv-mcp-ai já escreveu diretamente o status/processed_bucket/processed_object_key
 * de cada legal_documents que processou (spec Fase 2, seção 3); este use case cuida
 * apenas do que é responsabilidade do Orchestrator: status da sessão e auditoria.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyMcpResultUseCase {

    private static final java.util.Set<String> VALID_STATUSES =
            java.util.Set.of("COMPLETED", "PARTIALLY_COMPLETED", "FAILED");

    private static final Set<String> AI_ANALYSIS_ELIGIBLE_STATUSES = Set.of("COMPLETED", "PARTIALLY_COMPLETED");

    private final TriageSessionRepository sessionRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void execute(UUID sessionId, McpResultCallbackRequest request) {
        if (!sessionId.equals(request.getSessionId())) {
            throw new ValidationException("sessionId do path não corresponde ao do payload");
        }
        if (!VALID_STATUSES.contains(request.getStatus())) {
            throw new ValidationException("status inválido: " + request.getStatus());
        }

        TriageSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        session.setStatus(SessionStatus.valueOf(request.getStatus()));
        sessionRepository.save(session);

        int processedGroups = request.getProcessedGroups() == null ? 0 : request.getProcessedGroups().size();
        int failedDocuments = request.getFailedDocuments() == null ? 0 : request.getFailedDocuments().size();

        auditService.record(sessionId, session.getLawFirm().getId(), request.getCorrelationId(),
                EventType.MCP_RESULT_RECEIVED,
                "Resultado do MCP recebido: status=%s, gruposProcessados=%d, documentosFalhos=%d"
                        .formatted(request.getStatus(), processedGroups, failedDocuments));

        log.info("MCP result applied: sessionId={}, status={}, processedGroups={}, failedDocuments={}",
                sessionId, request.getStatus(), processedGroups, failedDocuments);

        if (AI_ANALYSIS_ELIGIBLE_STATUSES.contains(request.getStatus())) {
            LegalCase legalCase = session.getLegalCase();
            eventPublisher.publishEvent(new AiAnalysisTriggeredEvent(
                    sessionId, session.getLawFirm().getId(), request.getCorrelationId(), session.getProtocolo(),
                    legalCase.getId(), legalCase.getAreaJuridica(), legalCase.getTipoCaso(), legalCase.getTitulo(),
                    session.getCreatedAt(), java.time.LocalDateTime.now(),
                    request.getProcessedGroups(), request.getFailedDocuments()));
        }
    }
}
