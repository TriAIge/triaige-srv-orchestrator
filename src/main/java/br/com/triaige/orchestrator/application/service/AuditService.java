package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.AuditEvent;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.infrastructure.persistence.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(UUID sessionId, UUID lawFirmId, UUID correlationId,
                              EventType eventType, String description) {
        AuditEvent event = AuditEvent.builder()
                .sessionId(sessionId)
                .lawFirmId(lawFirmId)
                .correlationId(correlationId)
                .eventType(eventType)
                .description(description)
                .build();

        AuditEvent saved = repository.save(event);
        log.info("Audit event recorded: sessionId={}, correlationId={}, eventType={}",
                sessionId, correlationId, eventType.getValue());
        return saved;
    }
}
