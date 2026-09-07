package br.com.triaige.orchestrator.domain.enums;

import lombok.Getter;

/**
 * Espelha br.com.triaige.mcpai.domain.enums.AiToolName (schema compartilhado, script.sql).
 * O Orchestrator só precisa de JURISPRUDENCE_QUERY: é o único tool_name que este serviço
 * grava, ao criar o registro-stub em ai_tool_calls que satisfaz a FK local de
 * triage_results.jurisprudence_call_id (spec Fase 4, ver TriggerAiAnalysisUseCase).
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
