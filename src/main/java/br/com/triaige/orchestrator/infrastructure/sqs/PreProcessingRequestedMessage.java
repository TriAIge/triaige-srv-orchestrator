package br.com.triaige.orchestrator.infrastructure.sqs;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PreProcessingRequestedMessage {

    private String eventType;
    private UUID sessionId;
    private UUID caseId;
    private UUID lawFirmId;
    private UUID correlationId;
    private String protocolo;
    private List<DocumentPointer> documents;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentPointer {
        private UUID documentId;
        private String tipoDocumento;
        private String bucket;
        private String objectKey;
        private String contentType;
    }
}
