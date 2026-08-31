package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class InvalidDocumentStateException extends OrchestratorException {

    public InvalidDocumentStateException(String message) {
        super("INVALID_DOCUMENT_STATE", HttpStatus.CONFLICT, message);
    }
}
