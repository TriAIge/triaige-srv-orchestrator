package br.com.triaige.orchestrator.application.service;

import br.com.triaige.orchestrator.domain.entity.IdempotencyRecord;
import br.com.triaige.orchestrator.domain.entity.IdempotencyRecordId;
import br.com.triaige.orchestrator.domain.exception.IdempotencyKeyConflictException;
import br.com.triaige.orchestrator.domain.exception.MissingIdempotencyKeyException;
import br.com.triaige.orchestrator.infrastructure.config.OrchestratorProperties;
import br.com.triaige.orchestrator.infrastructure.persistence.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyServiceTest {

    private IdempotencyRecordRepository repository;
    private IdempotencyService service;

    private record Payload(String value) {
    }

    private record Response(String result) {
    }

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRecordRepository.class);
        OrchestratorProperties properties = new OrchestratorProperties();
        service = new IdempotencyService(repository, new ObjectMapper(), properties);
    }

    @Test
    void execute_firstCall_runsActionAndStoresRecord() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        AtomicInteger callCount = new AtomicInteger();

        ResponseEntity<Response> response = service.execute(
                UUID.randomUUID().toString(), "POST /sessions", new Payload("a"), Response.class,
                () -> {
                    callCount.incrementAndGet();
                    return ResponseEntity.status(HttpStatus.CREATED).body(new Response("ok"));
                });

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().result()).isEqualTo("ok");
        verify(repository).save(any(IdempotencyRecord.class));
    }

    @Test
    void execute_replayWithSamePayload_returnsStoredResponseWithoutRunningAction() throws Exception {
        String key = UUID.randomUUID().toString();
        Payload payload = new Payload("a");
        ObjectMapper mapper = new ObjectMapper();
        String requestHash = sha256(mapper.writeValueAsString(payload));

        IdempotencyRecord stored = IdempotencyRecord.builder()
                .id(new IdempotencyRecordId(key, "POST /sessions"))
                .requestHash(requestHash)
                .responseBody(mapper.writeValueAsString(new Response("cached")))
                .statusCode(201)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        when(repository.findById(any())).thenReturn(Optional.of(stored));

        AtomicInteger callCount = new AtomicInteger();
        ResponseEntity<Response> response = service.execute(key, "POST /sessions", payload, Response.class,
                () -> {
                    callCount.incrementAndGet();
                    return ResponseEntity.status(HttpStatus.CREATED).body(new Response("new"));
                });

        assertThat(callCount.get()).isZero();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().result()).isEqualTo("cached");
        verify(repository, never()).save(any());
    }

    @Test
    void execute_replayWithDifferentPayload_throwsConflict() {
        String key = UUID.randomUUID().toString();
        IdempotencyRecord stored = IdempotencyRecord.builder()
                .id(new IdempotencyRecordId(key, "POST /sessions"))
                .requestHash("different-hash")
                .responseBody("{}")
                .statusCode(201)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        when(repository.findById(any())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.execute(key, "POST /sessions", new Payload("a"), Response.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(new Response("new"))))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void execute_missingIdempotencyKey_throws() {
        assertThatThrownBy(() -> service.execute(null, "POST /sessions", new Payload("a"), Response.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(new Response("new"))))
                .isInstanceOf(MissingIdempotencyKeyException.class);

        assertThatThrownBy(() -> service.execute("not-a-uuid", "POST /sessions", new Payload("a"), Response.class,
                () -> ResponseEntity.status(HttpStatus.CREATED).body(new Response("new"))))
                .isInstanceOf(MissingIdempotencyKeyException.class);
    }

    private String sha256(String value) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
