package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.AuditEvent;
import br.com.triaige.orchestrator.domain.enums.EventType;
import br.com.triaige.orchestrator.infrastructure.persistence.AuditEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(UUID sessionId, UUID correlationId, EventType eventType, String description) {
        return record(sessionId, correlationId, eventType, description, null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(UUID sessionId, UUID correlationId, EventType eventType,
                              String description, Object payload) {
        String payloadJson = null;
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                log.warn("Could not serialize audit payload for event {}", eventType, e);
            }
        }

        AuditEvent event = AuditEvent.builder()
                .sessionId(sessionId)
                .correlationId(correlationId)
                .eventType(eventType)
                .description(description)
                .payloadJson(payloadJson)
                .build();

        AuditEvent saved = repository.save(event);
        log.info("Audit event recorded: sessionId={}, correlationId={}, eventType={}",
                sessionId, correlationId, eventType);
        return saved;
    }

    public boolean eventAlreadyRecorded(UUID sessionId, EventType eventType) {
        return repository.existsBySessionIdAndEventType(sessionId, eventType);
    }
}
