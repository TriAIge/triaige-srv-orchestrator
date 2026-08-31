package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends OrchestratorException {

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT,
                "Idempotency-Key já usada com payload diferente do original: " + idempotencyKey);
    }
}
