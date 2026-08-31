package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class DocumentStorageException extends OrchestratorException {

    public DocumentStorageException(String message, Throwable cause) {
        super("DOCUMENT_STORAGE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
