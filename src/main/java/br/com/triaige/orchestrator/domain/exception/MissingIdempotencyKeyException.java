package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class MissingIdempotencyKeyException extends OrchestratorException {

    public MissingIdempotencyKeyException() {
        super("MISSING_IDEMPOTENCY_KEY", HttpStatus.BAD_REQUEST,
                "Header Idempotency-Key é obrigatório e deve ser um UUID válido");
    }
}
