package br.com.triaige.orchestrator.infrastructure.http.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Corpo de {@code POST /api/ai/v1/analyze} no triaige-srv-mcp-ai (Fase 3, seção 4.1). Cópia
 * local do contrato — não há módulo compartilhado entre os dois serviços, mesma convenção já
 * usada para {@code McpResultCallbackRequest} (direção oposta).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestDto {

    @Builder.Default
    private String schemaVersion = "1.0";

    private UUID sessionId;
    private UUID correlationId;
    private String protocolo;
    private LegalCaseDto legalCase;
    private List<ProcessedGroupDto> processedGroups;
    private List<FailedDocumentDto> failedDocuments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LegalCaseDto {
        private UUID id;
        private String titulo;
        private String areaJuridica;
        private String tipoCaso;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedGroupDto {
        private UUID attachmentGroupId;
        private String trustedBucket;
        private String trustedObjectKey;
        private String tipoDocumento;
        private boolean resumido;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedDocumentDto {
        private UUID documentId;
        private String errorMessage;
    }
}
