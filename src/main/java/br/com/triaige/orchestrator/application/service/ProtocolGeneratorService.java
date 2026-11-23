package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.infrastructure.persistence.ProtocolSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Entra na transação de criação da sessão (REQUIRED) para que um rollback não deixe número de
 * protocolo pulado.
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
        log.info("Protocolo gerado: {}", protocolo);
        return protocolo;
    }
}
