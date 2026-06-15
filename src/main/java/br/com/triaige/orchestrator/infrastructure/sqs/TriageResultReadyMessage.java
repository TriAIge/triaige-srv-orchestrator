package br.com.triaige.orchestrator.infrastructure.sqs;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageResultReadyMessage {

    private String eventType;
    private UUID sessionId;
    private UUID caseId;
    private UUID lawFirmId;
    private UUID correlationId;
    private String protocolo;
    private String resultBucket;
    private String resultObjectKey;
    private RecipientInfo recipient;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RecipientInfo {
        private String name;
        private String email;
        private String phone;
        private String preferredChannel;
    }
}
