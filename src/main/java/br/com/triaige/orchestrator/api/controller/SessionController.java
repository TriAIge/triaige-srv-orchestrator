package br.com.triaige.orchestrator.api.controller;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.request.PresignDocumentRequest;
import br.com.triaige.orchestrator.api.dto.response.CompleteDocumentResponse;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.FinalizeSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.PresignDocumentResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.application.service.IdempotencyService;
import br.com.triaige.orchestrator.application.usecase.CompleteDocumentUploadUseCase;
import br.com.triaige.orchestrator.application.usecase.CreateSessionUseCase;
import br.com.triaige.orchestrator.application.usecase.FinalizeSessionUseCase;
import br.com.triaige.orchestrator.application.usecase.GetSessionStatusUseCase;
import br.com.triaige.orchestrator.application.usecase.PresignDocumentUploadUseCase;
import br.com.triaige.orchestrator.infrastructure.security.ApiCredentialAuthFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("${orchestrator.base-path}/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final CreateSessionUseCase createSessionUseCase;
    private final PresignDocumentUploadUseCase presignDocumentUploadUseCase;
    private final CompleteDocumentUploadUseCase completeDocumentUploadUseCase;
    private final FinalizeSessionUseCase finalizeSessionUseCase;
    private final GetSessionStatusUseCase getSessionStatusUseCase;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<CreateSessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(ApiCredentialAuthFilter.LAW_FIRM_ID_ATTRIBUTE) UUID lawFirmId,
            @RequestAttribute(ApiCredentialAuthFilter.API_CREDENTIAL_ID_ATTRIBUTE) UUID apiCredentialId) {

        return idempotencyService.execute(idempotencyKey, "POST /sessions", request, CreateSessionResponse.class,
                () -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(createSessionUseCase.execute(request, lawFirmId, apiCredentialId)));
    }

    @PostMapping("/{sessionId}/documents/presign")
    public ResponseEntity<PresignDocumentResponse> presignDocument(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PresignDocumentRequest request,
            @RequestAttribute(ApiCredentialAuthFilter.LAW_FIRM_ID_ATTRIBUTE) UUID lawFirmId) {

        PresignDocumentResponse response = presignDocumentUploadUseCase.execute(sessionId, lawFirmId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{sessionId}/documents/{documentId}/complete")
    public ResponseEntity<CompleteDocumentResponse> completeDocument(
            @PathVariable UUID sessionId,
            @PathVariable UUID documentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(ApiCredentialAuthFilter.LAW_FIRM_ID_ATTRIBUTE) UUID lawFirmId) {

        return idempotencyService.execute(idempotencyKey, "POST /sessions/{sessionId}/documents/{documentId}/complete",
                new Object[]{sessionId, documentId}, CompleteDocumentResponse.class,
                () -> ResponseEntity.ok(completeDocumentUploadUseCase.execute(sessionId, documentId, lawFirmId)));
    }

    @PostMapping("/{sessionId}/finalize")
    public ResponseEntity<FinalizeSessionResponse> finalizeSession(
            @PathVariable UUID sessionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(ApiCredentialAuthFilter.LAW_FIRM_ID_ATTRIBUTE) UUID lawFirmId) {

        return idempotencyService.execute(idempotencyKey, "POST /sessions/{sessionId}/finalize",
                sessionId, FinalizeSessionResponse.class,
                () -> ResponseEntity.ok(finalizeSessionUseCase.execute(sessionId, lawFirmId)));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionDetailResponse> getSession(
            @PathVariable UUID sessionId,
            @RequestAttribute(ApiCredentialAuthFilter.LAW_FIRM_ID_ATTRIBUTE) UUID lawFirmId) {

        return ResponseEntity.ok(getSessionStatusUseCase.execute(sessionId, lawFirmId));
    }
}
