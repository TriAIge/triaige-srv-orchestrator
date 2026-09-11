package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.NotificationResultCallbackRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.domain.exception.ValidationException;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * POST {basePath}/sessions/{sessionId}/notification-result — callback do triaige-srv-notification
 * ao concluir (sucesso ou falha) a notificação de todos os destinatários ativos de uma sessão,
 * fechando o gap identificado de a sessão nunca refletir se a notificação foi enviada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyNotificationResultUseCase {

    private static final Set<String> VALID_STATUSES = Set.of("NOTIFIED", "NOTIFICATION_FAILED");

    private final TriageSessionRepository sessionRepository;
    private final AuditService auditService;

    @Transactional
    public void execute(UUID sessionId, NotificationResultCallbackRequest request) {
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

        EventType eventType = "NOTIFIED".equals(request.getStatus())
                ? EventType.NOTIFICATION_SENT
                : EventType.NOTIFICATION_FAILED;

        auditService.record(sessionId, session.getLawFirm().getId(), request.getCorrelationId(), eventType,
                "Resultado da notificação recebido: status=%s, notificados=%d, falhos=%d"
                        .formatted(request.getStatus(), request.getRecipientsNotified(), request.getRecipientsFailed()));

        log.info("Notification result applied: sessionId={}, status={}, notified={}, failed={}",
                sessionId, request.getStatus(), request.getRecipientsNotified(), request.getRecipientsFailed());
    }
}
