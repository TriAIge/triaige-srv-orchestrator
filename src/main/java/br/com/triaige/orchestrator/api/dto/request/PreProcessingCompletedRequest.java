package br.com.triaige.orchestrator.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PreProcessingCompletedRequest {

    @NotNull(message = "correlationId é obrigatório")
    private UUID correlationId;

    @NotEmpty(message = "processedDocuments é obrigatório e não pode ser vazio")
    @Valid
    private List<ProcessedDocumentInfo> processedDocuments;

    @Data
    public static class ProcessedDocumentInfo {

        @NotNull(message = "documentId é obrigatório")
        private UUID documentId;

        @NotBlank
        @Size(max = 200)
        private String rawBucket;

        @NotBlank
        @Size(max = 500)
        private String rawObjectKey;

        @NotBlank
        @Size(max = 200)
        private String processedBucket;

        @NotBlank
        @Size(max = 500)
        private String processedObjectKey;

        @NotBlank
        private String status;
    }
}
