package br.com.triaige.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "law_firm_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LawFirmContact {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "law_firm_id", nullable = false)
    private LawFirm lawFirm;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "canal_preferencial", nullable = false, length = 20)
    private String canalPreferencial;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
