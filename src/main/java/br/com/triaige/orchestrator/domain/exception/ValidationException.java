package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends OrchestratorException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
    }
}
