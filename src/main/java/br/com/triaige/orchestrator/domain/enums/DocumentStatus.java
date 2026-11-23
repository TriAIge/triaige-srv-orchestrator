package br.com.triaige.orchestrator.domain.enums;

/**
 * PENDING_UPLOAD a UPLOAD_FAILED são do orchestrator; os demais são gravados pelo mcp-ai na mesma
 * linha de legal_documents e precisam existir aqui para a leitura da sessão não falhar.
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
