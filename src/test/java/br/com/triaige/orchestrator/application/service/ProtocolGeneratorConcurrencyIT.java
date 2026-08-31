package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD (spec seção 11): "Geração de protocolo atômica e sem colisão sob
 * concorrência (testado com requisições paralelas)".
 */
class ProtocolGeneratorConcurrencyIT extends AbstractIntegrationTest {

    private static final int PARALLEL_REQUESTS = 30;

    @Autowired
    private ProtocolGeneratorService protocolGeneratorService;

    @Test
    void generateNextProtocol_underConcurrency_neverCollides() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> tasks = IntStream.range(0, PARALLEL_REQUESTS)
                    .<Callable<String>>mapToObj(i -> protocolGeneratorService::generateNextProtocol)
                    .collect(Collectors.toList());

            List<Future<String>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            List<String> protocols = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());

            assertThat(protocols).hasSize(PARALLEL_REQUESTS);
            Set<String> unique = Set.copyOf(protocols);
            assertThat(unique).as("todos os protocolos gerados devem ser únicos").hasSize(PARALLEL_REQUESTS);
        } finally {
            executor.shutdownNow();
        }
    }
}
