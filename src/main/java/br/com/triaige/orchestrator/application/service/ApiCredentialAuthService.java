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
 * Compara o SHA-256 do token Bearer com api_credentials.token_hash por índice único, o que evita
 * o vazamento por tempo de uma comparação de string.
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
