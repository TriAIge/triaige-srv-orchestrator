package br.com.triaige.orchestrator.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "protocol_sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProtocolSequence {

    @Id
    @Column(name = "year")
    private Integer year;

    @Column(name = "last_sequence", nullable = false)
    @Builder.Default
    private Long lastSequence = 0L;

    public long nextSequence() {
        this.lastSequence++;
        return this.lastSequence;
    }
}
