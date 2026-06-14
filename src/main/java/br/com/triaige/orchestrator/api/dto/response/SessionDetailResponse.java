package br.com.triaige.orchestrator.api.dto.response;

import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.DocumentType;
import br.com.triaige.orchestrator.domain.enums.LegalArea;
import br.com.triaige.orchestrator.domain.enums.CaseType;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDetailResponse {

    private UUID sessionId;
    private UUID caseId;
    private String protocolo;
    private UUID correlationId;
    private SessionStatus status;
    private LegalArea areaJuridica;
    private CaseType tipoCaso;
    private List<DocumentSummary> documents;
    private ResultSummary result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DocumentSummary {
        private UUID documentId;
        private String nomeArquivoOriginal;
        private DocumentType tipoDocumento;
        private DocumentStatus status;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResultSummary {
        private String resultBucket;
        private String resultObjectKey;
        private String summaryObjectKey;
    }
}
