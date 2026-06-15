package br.com.triaige.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "triage_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriageResult {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TriageSession session;

    @Column(name = "result_bucket", nullable = false, length = 200)
    private String resultBucket;

    @Column(name = "result_object_key", nullable = false, length = 500)
    private String resultObjectKey;

    @Column(name = "summary_object_key", length = 500)
    private String summaryObjectKey;

    @Column(name = "jurisprudence_used", nullable = false)
    @Builder.Default
    private Boolean jurisprudenceUsed = false;

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
}
