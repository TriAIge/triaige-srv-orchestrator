package br.com.triaige.orchestrator.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "orchestrator")
public class OrchestratorProperties {

    private String basePath = "/api/orchestrator/v1";
    private Idempotency idempotency = new Idempotency();
    private Presign presign = new Presign();
    private Internal internal = new Internal();
    private McpAi mcpAi = new McpAi();

    @Data
    public static class Idempotency {
        private int ttlHours = 24;
    }

    @Data
    public static class Presign {
        private int uploadUrlTtlMinutes = 15;
    }

    /** Autenticação de endpoints internos (rede interna, ex: callback do triaige-srv-mcp-ai), fora do esquema de Bearer token de api_credentials. */
    @Data
    public static class Internal {
        private String mcpCallbackToken = "dev-local-internal-token";
    }

    /** Fase 4, spec seção 2.5: client Orchestrator→mcp-ai (POST /api/ai/v1/analyze). */
    @Data
    public static class McpAi {
        private String baseUrl = "http://localhost:8084";
        private String analyzePath = "/api/ai/v1/analyze";
        private String analyzeToken = "dev-local-analyze-token";
        private int connectTimeoutMs = 5000;
        /** > ai.analysis.timeout-ms (120000) do mcp-ai, com folga. */
        private int readTimeoutMs = 130000;
    }
}
