package br.com.triaige.orchestrator.domain.enums;

/**
 * COMPLETED/PARTIALLY_COMPLETED/FAILED são setados por ApplyMcpResultUseCase a partir do
 * callback do triaige-srv-mcp-ai (spec da Fase 2, seção 6).
 *
 * <p>A partir da Fase 4 (spec-fase4-orchestrator-triagem-ia.md, seção 2.3), COMPLETED e
 * PARTIALLY_COMPLETED <b>deixam de ser terminais</b>: passam a significar "pipeline do MCP
 * concluído, aguardando análise de IA". FAILED continua terminal (nada a analisar). O fluxo
 * pós-MCP segue para ANALYZING e termina em ANALYSIS_COMPLETED ou ANALYSIS_FAILED.</p>
 */
public enum SessionStatus {
    RECEIVING_DOCUMENTS,
    QUEUED_FOR_PROCESSING,
    CANCELLED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED,
    ANALYZING,
    ANALYSIS_COMPLETED,
    ANALYSIS_FAILED
}
