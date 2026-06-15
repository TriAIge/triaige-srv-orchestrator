package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.AiProcessingCompletedRequest;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.NotificationRecipient;
import br.com.triaige.orchestrator.domain.entity.TriageResult;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.BusinessRuleException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageResultRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import br.com.triaige.orchestrator.infrastructure.sqs.TriageResultReadyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiProcessingCompletedUseCase {

    private final TriageSessionRepository sessionRepository;
    private final TriageResultRepository resultRepository;
    private final AuditService auditService;
    private final QueuePublisher queuePublisher;
    private final AwsProperties awsProperties;

    @Transactional
    public void execute(UUID sessionId, AiProcessingCompletedRequest request) {
        TriageSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.getCorrelationId().equals(request.getCorrelationId())) {
            throw new BusinessRuleException("correlationId inválido para esta sessão");
        }

        // Idempotência: se já concluída, não duplicar notificação
        if (session.getStatus() == SessionStatus.CONCLUIDA) {
            log.warn("AI processing callback received but session already CONCLUIDA. Skipping. sessionId={}",
                    sessionId);
            return;
        }

        SessionStatus previousStatus = session.getStatus();

        // Trata status de falha vindo do serviço de IA
        if ("FALHA".equalsIgnoreCase(request.getStatus())) {
            session.setStatus(SessionStatus.FALHA);
            sessionRepository.save(session);
            auditService.record(sessionId, session.getCorrelationId(), EventType.SESSION_FAILED,
                    "Processamento de IA reportou falha");
            log.error("AI processing reported failure for session: {}", sessionId);
            return;
        }

        // Salva os ponteiros do resultado
        TriageResult result = TriageResult.builder()
                .id(UUID.randomUUID())
                .session(session)
                .resultBucket(request.getResultBucket())
                .resultObjectKey(request.getResultObjectKey())
                .summaryObjectKey(request.getSummaryObjectKey())
                .jurisprudenceUsed(request.isJurisprudenceUsed())
                .build();
        resultRepository.save(result);

        session.setStatus(SessionStatus.CONCLUIDA);
        session.setResult(result);
        sessionRepository.save(session);

        // Monta a mensagem de notificação
        NotificationRecipient recipient = session.getRecipient();
        TriageResultReadyMessage message = TriageResultReadyMessage.builder()
                .eventType("TRIAGE_RESULT_READY")
                .sessionId(sessionId)
                .caseId(session.getLegalCase().getId())
                .lawFirmId(session.getLawFirm().getId())
                .correlationId(session.getCorrelationId())
                .protocolo(session.getProtocolo())
                .resultBucket(request.getResultBucket())
                .resultObjectKey(request.getResultObjectKey())
                .recipient(recipient != null ? TriageResultReadyMessage.RecipientInfo.builder()
                        .name(recipient.getNome())
                        .email(recipient.getEmail())
                        .phone(recipient.getTelefone())
                        .preferredChannel(recipient.getCanalPreferencial().name())
                        .build() : null)
                .build();

        queuePublisher.publish(awsProperties.getSqs().getResultsReadyQueueUrl(), message);

        auditService.record(
                sessionId,
                session.getCorrelationId(),
                EventType.AI_PROCESSING_COMPLETED,
                String.format("IA concluída. Resultado salvo. Status: %s -> %s",
                        previousStatus, SessionStatus.CONCLUIDA)
        );

        log.info("AI processing completed: sessionId={}, protocolo={}, jurisprudence={}, correlationId={}",
                sessionId, session.getProtocolo(), request.isJurisprudenceUsed(),
                session.getCorrelationId());
    }
}
