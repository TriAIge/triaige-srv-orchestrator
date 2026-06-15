package br.com.triaige.orchestrator.domain.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID documentId) {
        super("Documento não encontrado: " + documentId);
    }
}
