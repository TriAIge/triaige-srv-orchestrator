package br.com.triaige.orchestrator.infrastructure.s3;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StoredDocument {

    private String bucket;
    private String objectKey;
    private String contentType;
    private long sizeBytes;
}
