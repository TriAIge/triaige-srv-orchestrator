package br.com.triaige.orchestrator.domain.entity;

import br.com.triaige.orchestrator.domain.enums.CaseType;
import br.com.triaige.orchestrator.domain.enums.LegalArea;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "legal_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalCase {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TriageSession session;

    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_juridica", nullable = false, length = 30)
    private LegalArea areaJuridica;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_caso", nullable = false, length = 50)
    private CaseType tipoCaso;

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
