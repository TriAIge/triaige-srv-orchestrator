package br.com.triaige.orchestrator.domain.entity;

import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "triage_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "law_firm_id", nullable = false)
    private LawFirm lawFirm;

    @Column(name = "protocolo", nullable = false, unique = true, length = 30)
    private String protocolo;

    @Column(name = "correlation_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SessionStatus status;

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private LegalCase legalCase;

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NotificationRecipient recipient;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LegalDocument> documents = new ArrayList<>();

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private TriageResult result;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    public boolean canReceiveDocuments() {
        return this.status == SessionStatus.ABERTA;
    }

    public boolean canBeProcessed() {
        return this.status == SessionStatus.ABERTA
                && this.documents != null
                && !this.documents.isEmpty();
    }

    public boolean isCompleted() {
        return this.status == SessionStatus.CONCLUIDA;
    }

    public boolean isCancelled() {
        return this.status == SessionStatus.CANCELADA;
    }

    public boolean isFailed() {
        return this.status == SessionStatus.FALHA;
    }
}
