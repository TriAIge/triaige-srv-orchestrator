package br.com.triaige.orchestrator.api.controller;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.response.AuditEventResponse;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.RegisterDocumentResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.application.usecase.*;
import br.com.triaige.orchestrator.domain.enums.DocumentType;
import br.com.triaige.orchestrator.shared.util.CorrelationIdUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final CreateSessionUseCase createSessionUseCase;
    private final RegisterDocumentUseCase registerDocumentUseCase;
    private final TriggerProcessingUseCase triggerProcessingUseCase;
    private final GetSessionStatusUseCase getSessionStatusUseCase;

    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader,
            @RequestHeader(value = "X-Law-Firm-Id", required = false) String lawFirmIdHeader) {

        UUID correlationId = CorrelationIdUtil.resolve(correlationIdHeader);

        // O header de escritório sobrescreve o body, se presente
        UUID lawFirmIdFromHeader = parseLawFirmId(lawFirmIdHeader);
        if (lawFirmIdFromHeader != null) {
            request.setLawFirmId(lawFirmIdFromHeader);
        }

        log.info("POST /api/v1/sessions - lawFirmId={}, correlationId={}", request.getLawFirmId(), correlationId);

        CreateSessionResponse response = createSessionUseCase.execute(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/{sessionId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegisterDocumentResponse> registerDocument(
            @PathVariable UUID sessionId,
            @RequestPart("file") MultipartFile file,
            @RequestParam("tipoDocumento") DocumentType tipoDocumento,
            @RequestParam(value = "ultimoDocumento", required = false, defaultValue = "false") boolean ultimoDocumento,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationIdHeader,
            @RequestHeader(value = "X-Law-Firm-Id", required = false) String lawFirmIdHeader) {

        UUID correlationId = CorrelationIdUtil.resolve(correlationIdHeader);

        log.info("POST /api/v1/sessions/{}/documents - correlationId={}, ultimoDocumento={}",
                sessionId, correlationId, ultimoDocumento);

        RegisterDocumentResponse response = registerDocumentUseCase.execute(
                sessionId, file, tipoDocumento, parseLawFirmId(lawFirmIdHeader), correlationId, ultimoDocumento);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{sessionId}/process")
    public ResponseEntity<Void> triggerProcessing(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Law-Firm-Id", required = false) String lawFirmIdHeader) {

        log.info("POST /api/v1/sessions/{}/process", sessionId);

        triggerProcessingUseCase.execute(sessionId, parseLawFirmId(lawFirmIdHeader));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailResponse> getSession(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Law-Firm-Id", required = false) String lawFirmIdHeader) {

        SessionDetailResponse response = getSessionStatusUseCase.getSession(sessionId, parseLawFirmId(lawFirmIdHeader));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/events")
    public ResponseEntity<List<AuditEventResponse>> getEvents(
            @PathVariable UUID sessionId,
            @RequestHeader(value = "X-Law-Firm-Id", required = false) String lawFirmIdHeader) {

        List<AuditEventResponse> events = getSessionStatusUseCase.getEvents(sessionId, parseLawFirmId(lawFirmIdHeader));
        return ResponseEntity.ok(events);
    }

    private static UUID parseLawFirmId(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(headerValue.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
