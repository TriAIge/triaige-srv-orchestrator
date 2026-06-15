package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.ProtocolSequence;
import br.com.triaige.orchestrator.infrastructure.persistence.ProtocolSequenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolGeneratorService {

    private final ProtocolSequenceRepository repository;

    @Transactional
    public String generateNextProtocol() {
        int year = LocalDate.now().getYear();

        ProtocolSequence sequence = repository.findByYearForUpdate(year)
                .orElseGet(() -> repository.save(
                        ProtocolSequence.builder()
                                .year(year)
                                .lastSequence(0L)
                                .build()));

        long next = sequence.nextSequence();
        repository.save(sequence);

        return String.format("TRI-%d-%06d", year, next);
    }
}
