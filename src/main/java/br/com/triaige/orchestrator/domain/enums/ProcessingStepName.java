package br.com.triaige.orchestrator.domain.enums;

import lombok.Getter;

/** OCR..EVIDENCE_SUMMARIZATION são escritos pelo triaige-srv-mcp-ai na Fase 2 (spec seção 5), mesma tabela compartilhada processing_steps. */
@Getter
public enum ProcessingStepName {
    DOCUMENT_UPLOAD("document_upload"),
    SESSION_FINALIZE("session_finalize"),
    OCR("ocr"),
    ANONYMIZATION("anonymization"),
    ATTACHMENT_GROUPING("attachment_grouping"),
    EVIDENCE_SUMMARIZATION("evidence_summarization");

    private final String value;

    ProcessingStepName(String value) {
        this.value = value;
    }

    public static ProcessingStepName fromValue(String value) {
        for (ProcessingStepName candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown processing step name: " + value);
    }
}
