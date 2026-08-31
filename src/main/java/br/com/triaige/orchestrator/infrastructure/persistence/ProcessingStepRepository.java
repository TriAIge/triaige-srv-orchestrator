package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.ProcessingStep;
import br.com.triaige.orchestrator.domain.enums.ProcessingStepName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProcessingStepRepository extends JpaRepository<ProcessingStep, UUID> {

    Optional<ProcessingStep> findBySessionIdAndDocumentIdAndStepName(
            UUID sessionId, UUID documentId, ProcessingStepName stepName);
}
