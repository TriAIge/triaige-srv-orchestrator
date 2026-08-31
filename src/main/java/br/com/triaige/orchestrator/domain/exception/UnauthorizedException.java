package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends OrchestratorException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, message);
    }
}
