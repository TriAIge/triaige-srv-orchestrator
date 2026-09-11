package br.com.triaige.orchestrator.domain.enums;

/**
 * PENDING_UPLOAD..UPLOAD_FAILED são do Orchestrator. Os demais valores
 * (OCR_IN_PROGRESS..PROCESSING_FAILED) são escritos pelo triaige-srv-mcp-ai, na
 * mesma linha compartilhada de legal_documents — precisam existir aqui também para que a
 * leitura via GetSessionStatusUseCase não falhe ao hidratar um documento já processado
 * pelo MCP.
 */
public enum DocumentStatus {
    PENDING_UPLOAD,
    RECEIVED,
    QUEUED,
    UPLOAD_FAILED,
    OCR_IN_PROGRESS,
    OCR_DONE,
    ANONYMIZED,
    GROUPED,
    TRUSTED,
    OCR_FAILED,
    PROCESSING_FAILED
}
