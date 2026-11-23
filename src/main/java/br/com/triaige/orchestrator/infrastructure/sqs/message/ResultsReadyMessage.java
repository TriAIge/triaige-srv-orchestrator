package br.com.triaige.orchestrator.infrastructure.sqs.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Mensagem da fila triaige-results-ready, publicada só em sucesso e com o mínimo de dados. */
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
