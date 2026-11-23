package br.com.triaige.orchestrator.domain.enums;

import lombok.Getter;

/**
 * Mesmos valores do enum equivalente no mcp-ai. O orchestrator só grava JURISPRUDENCE_QUERY, na
 * linha que satisfaz a FK de triage_results.
 */
@Getter
public enum AiToolName {
    JURISPRUDENCE_QUERY("jurisprudence_query");

    private final String value;

    AiToolName(String value) {
        this.value = value;
    }

    public static AiToolName fromValue(String value) {
        for (AiToolName candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown tool name: " + value);
    }
}
