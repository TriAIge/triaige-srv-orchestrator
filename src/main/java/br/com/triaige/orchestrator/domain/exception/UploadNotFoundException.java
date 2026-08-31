package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class UploadNotFoundException extends OrchestratorException {

    public UploadNotFoundException(UUID documentId) {
        super("UPLOAD_NOT_FOUND", HttpStatus.CONFLICT,
                "Objeto ainda não presente no S3 para o documento: " + documentId);
    }
}
