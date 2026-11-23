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
 * Chama POST /api/ai/v1/analyze no mcp-ai. Só falha de rede (ResourceAccessException) é elegível
 * ao retry; erro HTTP do mcp-ai vira {@link McpAiHttpException} e não é repetido.
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
