package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepName;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepStatus;
import br.com.triaige.orchestrator.domain.exception.DocumentNotFoundException;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.ProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Marca o documento como RECEIVED. Idempotente por documentId porque também roda no consumidor da
 * triaige-docs-received, que recupera escritas que falharam no MySQL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentReceivedFinalizationService {

    private final LegalDocumentRepository legalDocumentRepository;
    private final ProcessingStepRepository processingStepRepository;
    private final AuditService auditService;

    @Transactional
    public void apply(UUID documentId, UUID sessionId, long tamanhoBytes, UUID correlationId) {
        LegalDocument document = legalDocumentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (document.getStatus() != DocumentStatus.PENDING_UPLOAD) {
            log.info("Documento {} já processado (status={}), ignorando reaplicação idempotente", documentId,
                    document.getStatus());
            return;
        }

        UUID lawFirmId = document.getSession().getLawFirm().getId();

        document.setStatus(DocumentStatus.RECEIVED);
        document.setTamanhoBytes(tamanhoBytes);
        legalDocumentRepository.save(document);

        processingStepRepository.findBySessionIdAndDocumentIdAndStepName(
                        sessionId, documentId, ProcessingStepName.DOCUMENT_UPLOAD)
                .ifPresent(step -> {
                    step.setStatus(ProcessingStepStatus.COMPLETED);
                    step.setFinishedAt(LocalDateTime.now());
                    processingStepRepository.save(step);
                });

        auditService.record(sessionId, lawFirmId, correlationId, EventType.DOCUMENT_UPLOADED,
                "Documento recebido: " + documentId);

        log.info("Recebimento do documento concluído: documentId={}, sessionId={}", documentId, sessionId);
    }
}
