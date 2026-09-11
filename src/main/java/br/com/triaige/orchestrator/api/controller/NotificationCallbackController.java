package br.com.triaige.orchestrator.api.controller;

import br.com.triaige.orchestrator.api.dto.request.NotificationResultCallbackRequest;
import br.com.triaige.orchestrator.application.usecase.ApplyNotificationResultUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Callback síncrono do triaige-srv-notification ao concluir a notificação de uma sessão.
 * Autenticado por {@link br.com.triaige.orchestrator.infrastructure.security.InternalTokenAuthFilter}
 * (X-Internal-Token), não pelo esquema de Bearer token do restante da API.
 */
@RestController
@RequestMapping("${orchestrator.base-path}/sessions")
@RequiredArgsConstructor
public class NotificationCallbackController {

    private final ApplyNotificationResultUseCase applyNotificationResultUseCase;

    @PostMapping("/{sessionId}/notification-result")
    public ResponseEntity<Void> receiveNotificationResult(@PathVariable UUID sessionId,
                                                            @Valid @RequestBody NotificationResultCallbackRequest request) {
        applyNotificationResultUseCase.execute(sessionId, request);
        return ResponseEntity.ok().build();
    }
}
