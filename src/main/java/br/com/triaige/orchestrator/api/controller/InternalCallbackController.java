package br.com.triaige.orchestrator.api.controller;

import br.com.triaige.orchestrator.api.dto.request.AiProcessingCompletedRequest;
import br.com.triaige.orchestrator.api.dto.request.PreProcessingCompletedRequest;
import br.com.triaige.orchestrator.application.usecase.AiProcessingCompletedUseCase;
import br.com.triaige.orchestrator.application.usecase.PreProcessingCompletedUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/internal/api/v1/sessions")
@RequiredArgsConstructor
public class InternalCallbackController {

    private final PreProcessingCompletedUseCase preProcessingCompletedUseCase;
    private final AiProcessingCompletedUseCase aiProcessingCompletedUseCase;

    @PostMapping("/{sessionId}/pre-processing/completed")
    public ResponseEntity<Void> preProcessingCompleted(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PreProcessingCompletedRequest request) {

        log.info("POST /internal/api/v1/sessions/{}/pre-processing/completed - correlationId={}",
                sessionId, request.getCorrelationId());

        preProcessingCompletedUseCase.execute(sessionId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{sessionId}/ai-processing/completed")
    public ResponseEntity<Void> aiProcessingCompleted(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AiProcessingCompletedRequest request) {

        log.info("POST /internal/api/v1/sessions/{}/ai-processing/completed - correlationId={}",
                sessionId, request.getCorrelationId());

        aiProcessingCompletedUseCase.execute(sessionId, request);
        return ResponseEntity.ok().build();
    }
}
