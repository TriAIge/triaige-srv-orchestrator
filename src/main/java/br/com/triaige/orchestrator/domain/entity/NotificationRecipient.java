package br.com.triaige.orchestrator.domain.entity;

import br.com.triaige.orchestrator.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "notification_recipients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRecipient {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private TriageSession session;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal_preferencial", nullable = false, length = 20)
    private NotificationChannel canalPreferencial;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
