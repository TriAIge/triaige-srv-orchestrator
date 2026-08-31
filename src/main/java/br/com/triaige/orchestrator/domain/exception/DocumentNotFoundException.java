package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentNotFoundException extends OrchestratorException {

    public DocumentNotFoundException(UUID documentId) {
        super("DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Documento não encontrado: " + documentId);
    }
}
