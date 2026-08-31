package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.ApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiCredentialRepository extends JpaRepository<ApiCredential, UUID> {

    Optional<ApiCredential> findByTokenHash(String tokenHash);
}
