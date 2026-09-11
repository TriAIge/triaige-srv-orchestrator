package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.LawFirmContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LawFirmContactRepository extends JpaRepository<LawFirmContact, UUID> {

    List<LawFirmContact> findByLawFirmIdAndAtivoTrue(UUID lawFirmId);
}
