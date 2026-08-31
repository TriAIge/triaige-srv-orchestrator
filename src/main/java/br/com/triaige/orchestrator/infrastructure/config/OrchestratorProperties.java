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
}
