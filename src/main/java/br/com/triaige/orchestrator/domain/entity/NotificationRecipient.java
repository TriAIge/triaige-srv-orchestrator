package br.com.triaige.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Espelha {@code notification_recipients}, lida pelo triaige-srv-notification (RecipientRepositoryPort)
 * para decidir quem notificar quando a análise de uma sessão termina. Os dados de contato ficam
 * denormalizados aqui (em vez de só a FK contact_id) porque {@code law_firm_contacts} pode mudar
 * depois da sessão ser criada, e a notificação deve refletir o destinatário no momento do caso.
 */
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private TriageSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private LawFirmContact contact;

    @Column(name = "nome", nullable = false, length = 200)
    private String nome;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "telefone", length = 30)
    private String telefone;

    @Column(name = "canal_preferencial", nullable = false, length = 20)
    private String canalPreferencial;

    @PrePersist
    protected void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }
}
