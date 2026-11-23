package br.com.triaige.orchestrator.infrastructure.render;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dados do relatório que vêm do orchestrator. hashAuditoriaLog é calculado pelo chamador sobre o
 * mesmo JSON gravado no S3, para que o hash exibido bata com o arquivo.
 */
public record TriageReportRenderContext(
        String protocolo,
        String nomeEscritorio,
        UUID escritorioId,
        LocalDateTime dataHoraRecebimento,
        LocalDateTime dataHoraProcessamento,
        String statusProcessamento,
        String hashAuditoriaLog) {
}
