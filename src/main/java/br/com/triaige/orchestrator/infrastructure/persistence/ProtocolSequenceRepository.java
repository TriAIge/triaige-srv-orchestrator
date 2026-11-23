package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.ProtocolSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * UPSERT atômico seguido de SELECT ... FOR UPDATE na mesma transação, para não pular número em
 * rollback. Usa sintaxe do MySQL (ON DUPLICATE KEY UPDATE).
 */
public interface ProtocolSequenceRepository extends JpaRepository<ProtocolSequence, Integer> {

    @Modifying
    @Query(value = "INSERT INTO protocol_sequences (year, last_sequence) VALUES (:year, 1) "
            + "ON DUPLICATE KEY UPDATE last_sequence = last_sequence + 1", nativeQuery = true)
    void upsertIncrement(@Param("year") int year);

    @Query(value = "SELECT last_sequence FROM protocol_sequences WHERE year = :year FOR UPDATE", nativeQuery = true)
    Long selectLastSequenceForUpdate(@Param("year") int year);
}
