package br.com.triaige.orchestrator.infrastructure.render;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Dados que {@link TriageReportRenderer} precisa e que não vêm do relatório estruturado em
 * si (Fase 4, spec seção 6): já conhecidos pelo Orchestrator antes/depois da chamada ao
 * mcp-ai. {@code hashAuditoriaLog} é calculado pelo chamador a partir do MESMO texto JSON
 * que será gravado em {@code .json} no S3 curated — nunca recalculado aqui, para garantir
 * que hash exibido e conteúdo persistido correspondam exatamente.
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
