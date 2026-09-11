package br.com.triaige.orchestrator.infrastructure.persistence;

import br.com.triaige.orchestrator.domain.entity.ProtocolSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * A geração de protocolo segue literalmente o algoritmo:
 * um UPSERT atômico seguido de SELECT ... FOR UPDATE na mesma transação, para
 * que o número nunca seja "furado" em caso de rollback. Depende de sintaxe
 * MySQL (ON DUPLICATE KEY UPDATE / FOR UPDATE) — não funciona no perfil dev (H2).
 */
public interface ProtocolSequenceRepository extends JpaRepository<ProtocolSequence, Integer> {

    @Modifying
    @Query(value = "INSERT INTO protocol_sequences (year, last_sequence) VALUES (:year, 1) "
            + "ON DUPLICATE KEY UPDATE last_sequence = last_sequence + 1", nativeQuery = true)
    void upsertIncrement(@Param("year") int year);

    @Query(value = "SELECT last_sequence FROM protocol_sequences WHERE year = :year FOR UPDATE", nativeQuery = true)
    Long selectLastSequenceForUpdate(@Param("year") int year);
}
