package br.com.triaige.orchestrator.domain.enums;

/**
 * COMPLETED/PARTIALLY_COMPLETED/FAILED são setados por ApplyMcpResultUseCase a partir do
 * callback do triaige-srv-mcp-ai.
 *
 * <p>COMPLETED e
 * PARTIALLY_COMPLETED <b>deixam de ser terminais</b>: passam a significar "pipeline do MCP
 * concluído, aguardando análise de IA". FAILED continua terminal (nada a analisar). O fluxo
 * pós-MCP segue para ANALYZING e termina em ANALYSIS_COMPLETED ou ANALYSIS_FAILED.</p>
 *
 * <p>ANALYSIS_COMPLETED também deixa de ser terminal: publica em Q3 e aguarda o callback do
 * triaige-srv-notification (POST .../notification-result, ver ApplyNotificationResultUseCase)
 * para concluir em NOTIFIED ou NOTIFICATION_FAILED. ANALYSIS_FAILED não gera esse callback —
 * a sessão nunca chega a publicar em Q3.</p>
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
    ANALYSIS_FAILED,
    NOTIFIED,
    NOTIFICATION_FAILED
}
