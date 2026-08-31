package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.ApiCredential;
import br.com.triaige.orchestrator.domain.exception.UnauthorizedException;
import br.com.triaige.orchestrator.infrastructure.persistence.ApiCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Autenticação via api_credentials (spec seção 5): token do header Authorization
 * é hasheado (SHA-256) e comparado contra api_credentials.token_hash. A busca é
 * feita por índice único no hash (não por comparação sequencial do token em
 * texto claro), o que já evita o vazamento por timing de uma comparação
 * ingênua de string.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCredentialAuthService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiCredentialRepository apiCredentialRepository;

    @Transactional
    public AuthenticatedCredential authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException("Header Authorization ausente ou inválido");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Token ausente");
        }

        String tokenHash = hash(token);

        ApiCredential credential = apiCredentialRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Credencial inválida"));

        if (!credential.isUsable()) {
            throw new UnauthorizedException("Credencial inativa ou expirada");
        }

        credential.setLastUsedAt(LocalDateTime.now());
        apiCredentialRepository.save(credential);

        return new AuthenticatedCredential(credential.getLawFirm().getId(), credential.getId());
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }
}
