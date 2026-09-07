package br.com.triaige.orchestrator.domain.exception;

/** Falha ao renderizar o relatório final no template canônico (Fase 4, spec seção 6). */
public class TemplateRenderException extends RuntimeException {

    public TemplateRenderException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateRenderException(String message) {
        super(message);
    }
}
