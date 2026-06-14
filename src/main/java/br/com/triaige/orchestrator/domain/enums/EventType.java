package br.com.triaige.orchestrator.domain.enums;

public enum EventType {
    SESSION_CREATED,
    DOCUMENT_REGISTERED,
    PROCESSING_REQUESTED,
    PRE_PROCESSING_COMPLETED,
    AI_PROCESSING_COMPLETED,
    SESSION_FAILED,
    SESSION_CANCELLED
}
