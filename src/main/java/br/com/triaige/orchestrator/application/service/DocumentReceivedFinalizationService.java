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
 * Aplica o passo 4 de POST .../complete (spec seção 5.3): marca o documento como
 * RECEIVED e conclui o processing_step document_upload. Idempotente por documentId —
 * usada tanto pelo endpoint síncrono quanto pelo consumidor interno de Q1, que
 * reprocessa a mesma lógica caso a escrita síncrona no MySQL tenha falhado.
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
            log.info("Document {} already processed (status={}), skipping idempotent apply", documentId,
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

        log.info("Document received finalized: documentId={}, sessionId={}", documentId, sessionId);
    }
}
