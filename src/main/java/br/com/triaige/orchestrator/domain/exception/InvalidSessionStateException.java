package br.com.triaige.orchestrator.domain.exception;

import br.com.triaige.orchestrator.domain.enums.SessionStatus;

import java.util.UUID;

public class InvalidSessionStateException extends RuntimeException {

    public InvalidSessionStateException(UUID sessionId, SessionStatus currentStatus, String operation) {
        super(String.format("Sessão %s não pode executar a operação '%s' no status '%s'",
                sessionId, operation, currentStatus));
    }

    public InvalidSessionStateException(String message) {
        super(message);
    }
}
