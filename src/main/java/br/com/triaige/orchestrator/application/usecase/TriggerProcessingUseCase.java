package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.BusinessRuleException;
import br.com.triaige.orchestrator.domain.exception.InvalidSessionStateException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.sqs.PreProcessingRequestedMessage;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerProcessingUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository documentRepository;
    private final AuditService auditService;
    private final QueuePublisher queuePublisher;
    private final AwsProperties awsProperties;

    @Transactional
    public void execute(UUID sessionId, UUID lawFirmId) {
        TriageSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (lawFirmId != null && !session.getLawFirm().getId().equals(lawFirmId)) {
            throw new SessionNotFoundException(sessionId);
        }

        // BR: não é possível processar sessão cancelada
        if (session.isCancelled()) {
            throw new InvalidSessionStateException(sessionId, session.getStatus(), "processamento");
        }

        // BR: deve haver pelo menos um documento
        List<LegalDocument> docs = documentRepository.findBySessionId(sessionId);
        if (docs.isEmpty()) {
            throw new BusinessRuleException("Sessão não pode ser processada sem documentos registrados");
        }

        if (!session.canBeProcessed()) {
            throw new InvalidSessionStateException(sessionId, session.getStatus(), "processamento");
        }

        SessionStatus previousStatus = session.getStatus();
        session.setStatus(SessionStatus.AGUARDANDO_PRE_PROCESSAMENTO);
        docs.forEach(doc -> doc.setStatus(DocumentStatus.AGUARDANDO_PRE_PROCESSAMENTO));
        documentRepository.saveAll(docs);
        sessionRepository.save(session);

        // Monta a mensagem SQS — apenas ponteiros, sem conteúdo
        List<PreProcessingRequestedMessage.DocumentPointer> docPointers = docs.stream()
                .map(doc -> PreProcessingRequestedMessage.DocumentPointer.builder()
                        .documentId(doc.getId())
                        .tipoDocumento(doc.getTipoDocumento().name())
                        .bucket(doc.getRawBucket())
                        .objectKey(doc.getRawObjectKey())
                        .contentType(doc.getContentType())
                        .build())
                .toList();

        PreProcessingRequestedMessage message = PreProcessingRequestedMessage.builder()
                .eventType("PRE_PROCESSING_REQUESTED")
                .sessionId(sessionId)
                .caseId(session.getLegalCase().getId())
                .lawFirmId(session.getLawFirm().getId())
                .correlationId(session.getCorrelationId())
                .protocolo(session.getProtocolo())
                .documents(docPointers)
                .build();

        queuePublisher.publish(awsProperties.getSqs().getDocsPreprocessingQueueUrl(), message);

        auditService.record(
                sessionId,
                session.getCorrelationId(),
                EventType.PROCESSING_REQUESTED,
                String.format("Processamento disparado. Status: %s -> %s",
                        previousStatus, SessionStatus.AGUARDANDO_PRE_PROCESSAMENTO)
        );

        log.info("Processing triggered: sessionId={}, caseId={}, protocolo={}, documents={}, correlationId={}",
                sessionId, session.getLegalCase().getId(), session.getProtocolo(),
                docs.size(), session.getCorrelationId());
    }
}
