package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.ProcessingStep;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepName;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepStatus;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.ProcessingStepRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Passo 5 de POST .../finalize — transação MySQL após a publicação em Q2. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionFinalizationService {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final ProcessingStepRepository processingStepRepository;
    private final AuditService auditService;

    @Transactional
    public void finalizeSession(UUID sessionId, UUID lawFirmId, List<LegalDocument> receivedDocuments) {
        TriageSession session = sessionRepository.findByIdAndLawFirmId(sessionId, lawFirmId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        session.setStatus(SessionStatus.QUEUED_FOR_PROCESSING);
        sessionRepository.save(session);

        for (LegalDocument document : receivedDocuments) {
            document.setStatus(DocumentStatus.QUEUED);
            legalDocumentRepository.save(document);
        }

        ProcessingStep step = ProcessingStep.builder()
                .sessionId(sessionId)
                .stepName(ProcessingStepName.SESSION_FINALIZE)
                .status(ProcessingStepStatus.COMPLETED)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build();
        processingStepRepository.save(step);

        auditService.record(sessionId, lawFirmId, session.getCorrelationId(), EventType.SESSION_FINALIZED,
                "Sessão finalizada com " + receivedDocuments.size() + " documento(s)");

        log.info("Session finalized: sessionId={}, documentsCount={}", sessionId, receivedDocuments.size());
    }
}
