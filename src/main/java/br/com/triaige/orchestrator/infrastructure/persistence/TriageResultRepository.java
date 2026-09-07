package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.TriageResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TriageResultRepository extends JpaRepository<TriageResult, UUID> {

    Optional<TriageResult> findBySessionId(UUID sessionId);
}
