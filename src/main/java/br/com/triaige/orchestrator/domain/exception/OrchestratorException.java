package br.com.triaige.orchestrator.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class OrchestratorException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    protected OrchestratorException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    protected OrchestratorException(String code, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }
}
