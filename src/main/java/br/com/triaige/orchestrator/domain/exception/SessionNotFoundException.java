package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SessionNotFoundException extends OrchestratorException {

    public SessionNotFoundException(UUID sessionId) {
        super("SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "Sessão não encontrada: " + sessionId);
    }
}
