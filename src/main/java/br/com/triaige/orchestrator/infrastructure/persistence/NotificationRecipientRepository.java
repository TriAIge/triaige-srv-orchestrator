package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, UUID> {
}
