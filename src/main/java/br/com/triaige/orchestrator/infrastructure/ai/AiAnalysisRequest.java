package br.com.triaige.orchestrator.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO de transporte para a requisição REST síncrona ao triaige-srv-ai
 * (POST /internal/api/v1/analysis). Mantido propositalmente separado do
 * modelo de domínio do orquestrador.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiAnalysisRequest {

    private String correlationId;
    private String sessionId;
    private String caseId;
    private String tenantId;
    private String legalArea;
    private String s3Bucket;
    private List<DocumentRef> documents;
    private List<String> requestedTools;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentRef {
        private String documentId;
        private String documentType;
        private String s3Key;
    }
}
