package br.com.triaige.orchestrator.infrastructure.sqs.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Contrato Q1 (triaige-docs-received). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReceivedMessage {

    @Builder.Default
    private String schemaVersion = "1.0";

    @Builder.Default
    private String eventType = "document.received";

    private UUID documentId;
    private UUID sessionId;
    private UUID attachmentGroupId;
    private Integer partNumber;
    private String rawBucket;
    private String rawObjectKey;
    private Long tamanhoBytes;
    private UUID correlationId;
    private LocalDateTime receivedAt;
}
