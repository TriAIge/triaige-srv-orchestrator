package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.LawFirm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LawFirmRepository extends JpaRepository<LawFirm, UUID> {
}
