package br.com.triaige.orchestrator.domain.enums;

/** COMPLETED/PARTIALLY_COMPLETED/FAILED são setados por ApplyMcpResultUseCase a partir do callback do triaige-srv-mcp-ai (spec da Fase 2, seção 6). */
public enum SessionStatus {
    RECEIVING_DOCUMENTS,
    QUEUED_FOR_PROCESSING,
    CANCELLED,
    COMPLETED,
    PARTIALLY_COMPLETED,
    FAILED
}
