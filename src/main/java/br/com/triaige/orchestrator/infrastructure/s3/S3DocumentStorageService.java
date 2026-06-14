package br.com.triaige.orchestrator.infrastructure.s3;

import br.com.triaige.orchestrator.domain.exception.DocumentStorageException;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DocumentStorageService implements DocumentStoragePort {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    @Override
    public StoredDocument upload(UUID lawFirmId, UUID sessionId, MultipartFile file) {
        String bucket = awsProperties.getS3().getRawDocumentsBucket();
        String objectKey = buildObjectKey(lawFirmId, sessionId, file.getOriginalFilename());
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            log.info("Document uploaded to S3: bucket={}, objectKey={}, size={}",
                    bucket, objectKey, file.getSize());

            return StoredDocument.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .contentType(contentType)
                    .sizeBytes(file.getSize())
                    .build();

        } catch (IOException | software.amazon.awssdk.core.exception.SdkException e) {
            log.error("Failed to upload document to S3: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new DocumentStorageException("Falha ao enviar documento para o bucket: " + bucket, e);
        }
    }

    @Override
    public StoredDocument storeResult(UUID lawFirmId, UUID sessionId, String jsonContent) {
        String bucket = awsProperties.getS3().getCuratedResultsBucket();
        String objectKey = buildResultObjectKey(lawFirmId, sessionId);
        byte[] content = jsonContent.getBytes(StandardCharsets.UTF_8);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .contentLength((long) content.length)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            log.info("Analysis result stored to S3: bucket={}, objectKey={}, size={}",
                    bucket, objectKey, content.length);

            return StoredDocument.builder()
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .sizeBytes(content.length)
                    .build();

        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            log.error("Failed to store analysis result to S3: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new DocumentStorageException("Falha ao salvar resultado da análise no bucket: " + bucket, e);
        }
    }

    private String buildObjectKey(UUID lawFirmId, UUID sessionId, String originalFilename) {
        String safeName = originalFilename != null ? originalFilename : "documento";
        return "law-firm/%s/sessions/%s/raw/%s-%s".formatted(lawFirmId, sessionId, UUID.randomUUID(), safeName);
    }

    private String buildResultObjectKey(UUID lawFirmId, UUID sessionId) {
        return "law-firm/%s/sessions/%s/curated/%s-result.json".formatted(lawFirmId, sessionId, UUID.randomUUID());
    }
}
