package br.com.triaige.orchestrator.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bean único (não recriado por chamada, ao contrário de {@code OrchestratorCallbackClient}
 * do mcp-ai) — necessário para permitir {@code MockRestServiceServer.bindTo(...)} em teste
 * (spec Fase 4, plano de implementação, seção 2.5).
 */
@Configuration
@RequiredArgsConstructor
public class McpAiClientConfig {

    private final OrchestratorProperties orchestratorProperties;

    @Bean
    public RestClient mcpAiRestClient() {
        OrchestratorProperties.McpAi config = orchestratorProperties.getMcpAi();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(config.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
