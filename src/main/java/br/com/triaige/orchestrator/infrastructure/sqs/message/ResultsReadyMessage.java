package br.com.triaige.orchestrator.infrastructure.sqs.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Contrato Q3 (triaige-results-ready) — Fase 4, spec seção 4, passo 10. Payload mínimo, publicado só em sucesso. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultsReadyMessage {

    @Builder.Default
    private String schemaVersion = "1.0";

    private UUID sessionId;
    private UUID correlationId;
    private String protocolo;
    private UUID lawFirmId;
    private String resultBucket;
    private String resultObjectKey;
    private String summaryObjectKey;
}
