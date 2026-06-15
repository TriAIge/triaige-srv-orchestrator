package br.com.triaige.orchestrator.infrastructure.sqs;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentReceivedMessage {

    private String eventType;
    private UUID sessionId;
    private UUID tenantId;
    private UUID correlationId;
    private String protocolo;
    private String bucket;
    private String objectKey;
    private Instant createdAt;
}
