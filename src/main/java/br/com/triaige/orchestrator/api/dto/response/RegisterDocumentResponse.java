package br.com.triaige.orchestrator.api.dto.response;

import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class RegisterDocumentResponse {

    private UUID documentId;
    private UUID sessionId;
    private DocumentStatus status;
    private String bucket;
    private String objectKey;
    private boolean processingTriggered;
}
