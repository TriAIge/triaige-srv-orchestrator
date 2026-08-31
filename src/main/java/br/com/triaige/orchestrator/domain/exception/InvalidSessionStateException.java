package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class InvalidSessionStateException extends OrchestratorException {

    public InvalidSessionStateException(String message) {
        super("INVALID_SESSION_STATE", HttpStatus.CONFLICT, message);
    }
}
