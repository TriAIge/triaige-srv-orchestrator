package br.com.triaige.orchestrator.shared.util;

import java.util.UUID;

public final class CorrelationIdUtil {

    private CorrelationIdUtil() {}

    /**
     * Resolve o correlationId a partir de um valor de cabeçalho recebido.
     * Se o cabeçalho estiver ausente ou em branco, um novo UUID é gerado.
     */
    public static UUID resolve(String headerValue) {
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                return UUID.fromString(headerValue.trim());
            } catch (IllegalArgumentException ignored) {
                // segue em frente para gerar um novo
            }
        }
        return UUID.randomUUID();
    }
}
