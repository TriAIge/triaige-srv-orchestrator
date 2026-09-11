package br.com.triaige.orchestrator.domain.enums;

/**
 * Valores não são enumerados explicitamente (apenas exige
 * status = 'ACTIVE'); ACTIVE/INACTIVE/REVOKED é a suposição conservadora adotada.
 */
public enum CredentialStatus {
    ACTIVE,
    INACTIVE,
    REVOKED
}
