package br.com.triaige.orchestrator.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class AiProcessingCompletedRequest {

    @NotNull(message = "correlationId é obrigatório")
    private UUID correlationId;

    @NotBlank(message = "status é obrigatório")
    private String status;

    @NotBlank(message = "resultBucket é obrigatório")
    @Size(max = 200)
    private String resultBucket;

    @NotBlank(message = "resultObjectKey é obrigatório")
    @Size(max = 500)
    private String resultObjectKey;

    @Size(max = 500)
    private String summaryObjectKey;

    private boolean jurisprudenceUsed;
}
