package br.com.triaige.orchestrator.application.usecase;

import br.com.triaige.orchestrator.api.dto.request.PresignDocumentRequest;
import br.com.triaige.orchestrator.api.dto.response.PresignDocumentResponse;
import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.entity.ProcessingStep;
import br.com.triaige.orchestrator.domain.entity.TriageSession;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepName;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepStatus;
import br.com.triaige.orchestrator.domain.exception.InvalidSessionStateException;
import br.com.triaige.orchestrator.domain.exception.SessionNotFoundException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.LegalDocumentRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.ProcessingStepRepository;
import br.com.triaige.orchestrator.infrastructure.persistence.TriageSessionRepository;
import br.com.triaige.orchestrator.infrastructure.s3.S3DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresignDocumentUploadUseCase {

    private final TriageSessionRepository sessionRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final ProcessingStepRepository processingStepRepository;
    private final S3DocumentStorageService s3DocumentStorageService;
    private final AwsProperties awsProperties;

    @Transactional
    public PresignDocumentResponse execute(UUID sessionId, UUID lawFirmId, PresignDocumentRequest request) {
        TriageSession session = sessionRepository.findByIdAndLawFirmId(sessionId, lawFirmId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (!session.canReceiveDocuments()) {
            throw new InvalidSessionStateException(
                    "Sessão " + sessionId + " não está em RECEIVING_DOCUMENTS (status atual: " + session.getStatus() + ")");
        }

        UUID documentId = UUID.randomUUID();
        UUID attachmentGroupId = request.getAttachmentGroupId() != null
                ? request.getAttachmentGroupId()
                : UUID.randomUUID();
        int partNumber = request.getPartNumber() != null ? request.getPartNumber() : 1;

        String rawObjectKey = "raw/%s/%s/%s/%d-%s".formatted(
                lawFirmId, sessionId, attachmentGroupId, partNumber, documentId);
        String rawBucket = awsProperties.getS3().getRawDocumentsBucket();

        LegalDocument document = LegalDocument.builder()
                .id(documentId)
                .session(session)
                .attachmentGroupId(attachmentGroupId)
                .partNumber(partNumber)
                .nomeArquivoOriginal(request.getNomeArquivoOriginal())
                .tipoDocumento(request.getTipoDocumento())
                .contentType(request.getContentType())
                .rawBucket(rawBucket)
                .rawObjectKey(rawObjectKey)
                .status(DocumentStatus.PENDING_UPLOAD)
                .build();
        legalDocumentRepository.save(document);

        ProcessingStep step = ProcessingStep.builder()
                .sessionId(sessionId)
                .documentId(documentId)
                .stepName(ProcessingStepName.DOCUMENT_UPLOAD)
                .status(ProcessingStepStatus.PENDING)
                .build();
        processingStepRepository.save(step);

        PresignedPutObjectRequest presigned = s3DocumentStorageService.presignPutObject(
                rawBucket, rawObjectKey, request.getContentType());

        LocalDateTime expiresAt = LocalDateTime.ofInstant(presigned.expiration(), ZoneId.systemDefault());

        log.info("Presigned upload URL generated: documentId={}, sessionId={}, rawObjectKey={}",
                documentId, sessionId, rawObjectKey);

        return PresignDocumentResponse.builder()
                .documentId(documentId)
                .attachmentGroupId(attachmentGroupId)
                .partNumber(partNumber)
                .uploadUrl(presigned.url().toString())
                .uploadUrlExpiresAt(expiresAt)
                .rawBucket(rawBucket)
                .rawObjectKey(rawObjectKey)
                .build();
    }
}
