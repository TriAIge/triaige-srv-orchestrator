package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.TriageSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TriageSessionRepository extends JpaRepository<TriageSession, UUID> {

    Optional<TriageSession> findByIdAndLawFirmId(UUID id, UUID lawFirmId);
}
