package br.com.triaige.orchestrator.infrastructure.s3;

import br.com.triaige.orchestrator.domain.exception.DocumentStorageException;
import br.com.triaige.orchestrator.infrastructure.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DocumentStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final OrchestratorProperties orchestratorProperties;

    public PresignedPutObjectRequest presignPutObject(String bucket, String objectKey, String contentType) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(
                            orchestratorProperties.getPresign().getUploadUrlTtlMinutes()))
                    .putObjectRequest(putObjectRequest)
                    .build();

            return s3Presigner.presignPutObject(presignRequest);
        } catch (S3Exception e) {
            log.error("Failed to presign PUT URL: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new DocumentStorageException("Falha ao gerar URL pré-assinada para upload", e);
        }
    }

    /**
     * Grava conteúdo diretamente no S3 (JSON estruturado e Markdown
     * renderizado em bucket-triaige-curated). Ao contrário de {@link #presignPutObject}, o
     * próprio Orchestrator faz o upload — não há cliente externo envolvido neste fluxo.
     */
    public void putObject(String bucket, String objectKey, byte[] content, String contentType) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            log.error("Failed to PutObject: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new DocumentStorageException(
                    "Falha ao gravar objeto no S3 curated: bucket=%s, objectKey=%s".formatted(bucket, objectKey), e);
        }
    }

    /** Retorna o tamanho do objeto se ele existir no S3, ou empty caso contrário. */
    public Optional<Long> headObject(String bucket, String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return Optional.of(response.contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            log.error("Failed to HeadObject: bucket={}, objectKey={}", bucket, objectKey, e);
            throw new DocumentStorageException("Falha ao verificar existência do objeto no S3", e);
        }
    }
}
