package br.com.triaige.orchestrator.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class AiServiceConfig {

    private final AiServiceProperties aiServiceProperties;

    @Bean
    public RestClient aiServiceRestClient() {
        return RestClient.builder()
                .baseUrl(aiServiceProperties.getBaseUrl())
                .build();
    }
}
