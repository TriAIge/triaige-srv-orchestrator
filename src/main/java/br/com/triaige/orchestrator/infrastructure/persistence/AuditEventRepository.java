package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.AuditEvent;
import br.com.triaige.orchestrator.domain.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    Optional<AuditEvent> findTopBySessionIdAndEventTypeOrderByCreatedAtDesc(UUID sessionId, EventType eventType);
    boolean existsBySessionIdAndEventType(UUID sessionId, EventType eventType);
}
