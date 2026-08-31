package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.response.CompleteDocumentResponse;
import br.com.triaige.orchestrator.application.service.DocumentReceivedFinalizationService;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.exception.DocumentNotFoundException;
import br.com.triaige.orchestrator.domain.exception.InvalidDocumentStateException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.domain.exception.UploadNotFoundException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.s3.S3DocumentStorageService;
import br.com.triaige.orchestrator.infrastructure.sqs.QueuePublisher;
import br.com.triaige.orchestrator.infrastructure.sqs.message.DocumentReceivedMessage;
import br.com.triaige.orchestrator.shared.metrics.IngestionMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Não é @Transactional no nível do use case: os passos 2 (HeadObject no S3) e 3
 * (publicar em Q1) precisam ocorrer ANTES da transação MySQL do passo 4, por
 * design (spec seção 2) — Q1 é o mecanismo de recuperação caso a escrita no
 * MySQL falhe. A transação em si vive em {@link DocumentReceivedFinalizationService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompleteDocumentUploadUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final S3DocumentStorageService s3DocumentStorageService;
    private final QueuePublisher queuePublisher;
    private final AwsProperties awsProperties;
    private final DocumentReceivedFinalizationService finalizationService;
    private final IngestionMetrics ingestionMetrics;

    public CompleteDocumentResponse execute(UUID sessionId, UUID documentId, UUID lawFirmId) {
        TriageSession session = sessionRepository.findByIdAndLawFirmId(sessionId, lawFirmId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        LegalDocument document = legalDocumentRepository.findByIdAndSessionId(documentId, sessionId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        if (document.getStatus() != DocumentStatus.PENDING_UPLOAD) {
            throw new InvalidDocumentStateException(
                    "Documento " + documentId + " não está PENDING_UPLOAD (status atual: " + document.getStatus() + ")");
        }

        long headObjectStartedAt = System.currentTimeMillis();
        long tamanhoBytes = s3DocumentStorageService.headObject(document.getRawBucket(), document.getRawObjectKey())
                .orElseThrow(() -> new UploadNotFoundException(documentId));
        ingestionMetrics.recordS3HeadObjectLatency(System.currentTimeMillis() - headObjectStartedAt);

        DocumentReceivedMessage message = DocumentReceivedMessage.builder()
                .documentId(documentId)
                .sessionId(sessionId)
                .attachmentGroupId(document.getAttachmentGroupId())
                .partNumber(document.getPartNumber())
                .rawBucket(document.getRawBucket())
                .rawObjectKey(document.getRawObjectKey())
                .tamanhoBytes(tamanhoBytes)
                .correlationId(session.getCorrelationId())
                .receivedAt(LocalDateTime.now())
                .build();
        long q1PublishStartedAt = System.currentTimeMillis();
        queuePublisher.publish(awsProperties.getSqs().getDocsReceivedQueueUrl(), message);
        ingestionMetrics.recordQ1PublishLatency(System.currentTimeMillis() - q1PublishStartedAt);

        finalizationService.apply(documentId, sessionId, tamanhoBytes, session.getCorrelationId());

        ingestionMetrics.incrementDocumentsReceived();

        log.info("Document upload completed: documentId={}, sessionId={}, tamanhoBytes={}",
                documentId, sessionId, tamanhoBytes);

        return CompleteDocumentResponse.builder()
                .documentId(documentId)
                .status(DocumentStatus.RECEIVED)
                .tamanhoBytes(tamanhoBytes)
                .build();
    }
}
