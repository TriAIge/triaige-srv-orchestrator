package br.com.triaige.orchestrator.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * DTO de transporte para a resposta da requisição REST síncrona ao
 * triaige-srv-ai (POST /internal/api/v1/analysis).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAnalysisResponse {

    private String correlationId;
    private String sessionId;
    private String caseId;
    private String tenantId;
    private String status;

    // Presente quando status == COMPLETED ou OUT_OF_SCOPE
    private JsonNode analysis;

    // Presente quando status == FAILED
    private AnalysisError error;

    private Metadata metadata;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AnalysisError {
        private String code;
        private String message;
        private boolean retryable;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private String model;
        private Instant processedAt;
        private Integer documentsAnalyzed;
        private List<String> toolsUsed;
        private Long geminiCallDurationMs;
    }
}
