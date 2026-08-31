package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.LegalDocument;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    Optional<LegalDocument> findByIdAndSessionId(UUID id, UUID sessionId);

    List<LegalDocument> findBySessionIdAndStatus(UUID sessionId, DocumentStatus status);

    List<LegalDocument> findBySessionId(UUID sessionId);
}
