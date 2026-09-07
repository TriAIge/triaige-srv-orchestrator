package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.AiToolCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiToolCallRepository extends JpaRepository<AiToolCall, UUID> {
}
