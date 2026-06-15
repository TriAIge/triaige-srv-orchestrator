package br.com.triaige.orchestrator.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "triaige.ai-service")
public class AiServiceProperties {

    private String baseUrl = "http://localhost:8082";
}
