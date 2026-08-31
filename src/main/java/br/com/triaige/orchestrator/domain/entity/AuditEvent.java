package br.com.triaige.orchestrator.domain.entity;

import br.com.triaige.orchestrator.domain.converter.EventTypeConverter;
import br.com.triaige.orchestrator.domain.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "session_id", columnDefinition = "CHAR(36)")
    private UUID sessionId;

    @Column(name = "law_firm_id", columnDefinition = "CHAR(36)")
    private UUID lawFirmId;

    @Column(name = "correlation_id", columnDefinition = "CHAR(36)")
    private UUID correlationId;

    @Convert(converter = EventTypeConverter.class)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
