package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.TriageSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TriageSessionRepository extends JpaRepository<TriageSession, UUID> {

    @Query("SELECT s FROM TriageSession s " +
           "LEFT JOIN FETCH s.legalCase " +
           "LEFT JOIN FETCH s.recipient " +
           "LEFT JOIN FETCH s.documents " +
           "LEFT JOIN FETCH s.result " +
           "WHERE s.id = :sessionId")
    Optional<TriageSession> findByIdWithDetails(@Param("sessionId") UUID sessionId);

    boolean existsByProtocolo(String protocolo);
}
