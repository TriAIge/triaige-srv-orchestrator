package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.infrastructure.persistence.ProtocolSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Geração atômica de protocolo. Propagation REQUIRED: quando
 * chamada de dentro da transação de criação da sessão (caso de uso normal),
 * junta-se a ela — mesma transação do INSERT em triage_sessions, para que um
 * rollback não deixe o número "furado". Também pode ser chamada isoladamente
 * (ex.: testes de concorrência), abrindo sua própria transação nesse caso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolGeneratorService {

    private static final String PROTOCOL_FORMAT = "TRI-%d-%06d";

    private final ProtocolSequenceRepository repository;

    @Transactional
    public String generateNextProtocol() {
        int year = LocalDate.now().getYear();

        repository.upsertIncrement(year);
        Long sequence = repository.selectLastSequenceForUpdate(year);

        String protocolo = PROTOCOL_FORMAT.formatted(year, sequence);
        log.info("Protocol generated: {}", protocolo);
        return protocolo;
    }
}
