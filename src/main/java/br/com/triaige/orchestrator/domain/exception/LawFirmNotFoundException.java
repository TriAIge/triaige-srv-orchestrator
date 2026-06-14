package br.com.triaige.orchestrator.domain.exception;

import java.util.UUID;

public class LawFirmNotFoundException extends RuntimeException {

    public LawFirmNotFoundException(UUID lawFirmId) {
        super("Escritório jurídico não encontrado: " + lawFirmId);
    }
}
