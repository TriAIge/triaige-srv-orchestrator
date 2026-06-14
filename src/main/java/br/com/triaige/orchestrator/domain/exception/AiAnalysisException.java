package br.com.triaige.orchestrator.domain.exception;

public class AiAnalysisException extends RuntimeException {
    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
