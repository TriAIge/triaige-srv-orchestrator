package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class NoDocumentsReceivedException extends OrchestratorException {

    public NoDocumentsReceivedException(UUID sessionId) {
        super("NO_DOCUMENTS_RECEIVED", HttpStatus.CONFLICT,
                "Nenhum documento RECEIVED vinculado à sessão: " + sessionId);
    }
}
