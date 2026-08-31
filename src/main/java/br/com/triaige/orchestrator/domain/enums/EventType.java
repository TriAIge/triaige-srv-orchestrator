package br.com.triaige.orchestrator.domain.enums;

import lombok.Getter;

@Getter
public enum EventType {
    SESSION_CREATED("session_created"),
    DOCUMENT_UPLOADED("document_uploaded"),
    SESSION_FINALIZED("session_finalized"),
    MCP_RESULT_RECEIVED("mcp_result_received");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    public static EventType fromValue(String value) {
        for (EventType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown event type: " + value);
    }
}
