package br.com.triaige.orchestrator.api.controller;

import br.com.triaige.orchestrator.api.dto.request.McpResultCallbackRequest;
import br.com.triaige.orchestrator.application.usecase.ApplyMcpResultUseCase;
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
 * Callback síncrono do triaige-srv-mcp-ai ao concluir o pipeline de uma sessão. Autenticado
 * por {@link br.com.triaige.orchestrator.infrastructure.security.InternalTokenAuthFilter}
 * (X-Internal-Token), não pelo esquema de Bearer token do restante da API.
 */
@RestController
@RequestMapping("${orchestrator.base-path}/sessions")
@RequiredArgsConstructor
public class McpCallbackController {

    private final ApplyMcpResultUseCase applyMcpResultUseCase;

    @PostMapping("/{sessionId}/mcp-result")
    public ResponseEntity<Void> receiveMcpResult(@PathVariable UUID sessionId,
                                                  @Valid @RequestBody McpResultCallbackRequest request) {
        applyMcpResultUseCase.execute(sessionId, request);
        return ResponseEntity.ok().build();
    }
}
