package br.com.triaige.orchestrator.domain.enums;

/**
 * Recebimento → pipeline do mcp-ai (COMPLETED e PARTIALLY_COMPLETED aguardam a análise; FAILED é
 * final) → ANALYZING → ANALYSIS_COMPLETED → NOTIFIED ou NOTIFICATION_FAILED.
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
