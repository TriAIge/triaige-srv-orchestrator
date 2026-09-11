package br.com.triaige.orchestrator.infrastructure.http;

import br.com.triaige.orchestrator.domain.exception.McpAiHttpException;
import br.com.triaige.orchestrator.infrastructure.config.OrchestratorProperties;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisRequestDto;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Client de saída Orchestrator → triaige-srv-mcp-ai:
 * {@code POST /api/ai/v1/analyze} com {@code X-Internal-Token} e
 * {@code Idempotency-Key = sessionId}. Falhas de rede/conexão (timeout, connection refused)
 * chegam ao chamador como {@link org.springframework.web.client.ResourceAccessException} —
 * a única categoria elegível ao retry único. Um erro HTTP explícito
 * (4xx/5xx) do mcp-ai é relançado como {@link McpAiHttpException}, nunca elegível a retry.
 */
@Component
@RequiredArgsConstructor
public class McpAiAnalysisClient {

    private final RestClient mcpAiRestClient;
    private final OrchestratorProperties orchestratorProperties;

    public AnalysisResponseDto analyze(AnalysisRequestDto request, UUID idempotencyKey) {
        OrchestratorProperties.McpAi config = orchestratorProperties.getMcpAi();
        return mcpAiRestClient.post()
                .uri(config.getAnalyzePath())
                .header("X-Internal-Token", config.getAnalyzeToken())
                .header("Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new McpAiHttpException(resp.getStatusCode(),
                            new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(AnalysisResponseDto.class);
    }
}
