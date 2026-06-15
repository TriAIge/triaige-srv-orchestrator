package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.TriageResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TriageResultRepository extends JpaRepository<TriageResult, UUID> {
    Optional<TriageResult> findBySessionId(UUID sessionId);
}
