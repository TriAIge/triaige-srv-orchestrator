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

    /** Tokens dos callbacks servidor-a-servidor (X-Internal-Token), separados das api_credentials. */
    @Data
    public static class Internal {
        private String mcpCallbackToken = "dev-local-internal-token";
        /** Callback do triaige-srv-notification (POST .../notification-result). */
        private String notificationCallbackToken = "dev-local-internal-token";
    }

    /** Client Orchestrator→mcp-ai (POST /api/ai/v1/analyze). */
    @Data
    public static class McpAi {
        private String baseUrl = "http://localhost:8084";
        private String analyzePath = "/api/ai/v1/analyze";
        private String analyzeToken = "dev-local-analyze-token";
        private int connectTimeoutMs = 5000;
        /** Maior que o ai.analysis.timeout-ms do mcp-ai (120000), com folga. */
        private int readTimeoutMs = 130000;
    }
}
