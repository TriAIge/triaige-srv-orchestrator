package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.RegisterDocumentResponse;
import br.com.triaige.orchestrator.application.service.AuditService;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.DocumentType;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.domain.exception.InvalidSessionStateException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.s3.DocumentStoragePort;
import br.com.triaige.orchestrator.infrastructure.s3.StoredDocument;
import br.com.triaige.orchestrator.infrastructure.sqs.DocumentReceivedMessage;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RegisterDocumentUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository documentRepository;
    private final AuditService auditService;
    private final DocumentStoragePort documentStoragePort;
    private final QueuePublisher queuePublisher;
    private final AwsProperties awsProperties;

    @Transactional
    public RegisterDocumentResponse execute(UUID sessionId, MultipartFile file, DocumentType tipoDocumento,
                                             UUID lawFirmId, UUID correlationId) {
        TriageSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        // Isolamento por escritório jurídico
        if (lawFirmId != null && !session.getLawFirm().getId().equals(lawFirmId)) {
            throw new SessionNotFoundException(sessionId);
        }

        // BR: concluded sessions cannot receive documents
        if (!session.canReceiveDocuments()) {
            throw new InvalidSessionStateException(sessionId, session.getStatus(),
                    "registro de documento");
        }

        StoredDocument stored = documentStoragePort.upload(session.getLawFirm().getId(), sessionId, file);

        LegalDocument document = LegalDocument.builder()
                .id(UUID.randomUUID())
                .session(session)
                .nomeArquivoOriginal(file.getOriginalFilename())
                .tipoDocumento(tipoDocumento)
                .contentType(stored.getContentType())
                .tamanhoBytes(stored.getSizeBytes())
                .rawBucket(stored.getBucket())
                .rawObjectKey(stored.getObjectKey())
                .status(DocumentStatus.REGISTRADO)
                .build();

        LegalDocument saved = documentRepository.save(document);

        UUID effectiveCorrelationId = correlationId != null ? correlationId : session.getCorrelationId();

        auditService.record(
                sessionId,
                effectiveCorrelationId,
                EventType.DOCUMENT_REGISTERED,
                "Documento registrado: " + file.getOriginalFilename()
        );

        // Notifica o recebimento do documento — apenas ponteiros, sem conteúdo
        DocumentReceivedMessage message = DocumentReceivedMessage.builder()
                .eventType("DOCUMENT_RECEIVED")
                .sessionId(sessionId)
                .tenantId(session.getLawFirm().getId())
                .correlationId(effectiveCorrelationId)
                .protocolo(session.getProtocolo())
                .bucket(saved.getRawBucket())
                .objectKey(saved.getRawObjectKey())
                .createdAt(Instant.now())
                .build();

        queuePublisher.publish(awsProperties.getSqs().getDocsReceivedQueueUrl(), message);

        log.info("Document registered: documentId={}, sessionId={}, file={}",
                saved.getId(), sessionId, file.getOriginalFilename());

        return RegisterDocumentResponse.builder()
                .documentId(saved.getId())
                .sessionId(sessionId)
                .status(DocumentStatus.REGISTRADO)
                .bucket(saved.getRawBucket())
                .objectKey(saved.getRawObjectKey())
                .build();
    }
}
