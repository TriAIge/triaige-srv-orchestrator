package br.com.triaige.orchestrator.infrastructure.ai;

import br.com.triaige.orchestrator.domain.exception.AiAnalysisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestAiAnalysisClient implements AiAnalysisClient {

    private static final String ANALYSIS_PATH = "/internal/api/v1/analysis";

    private final RestClient aiServiceRestClient;

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        try {
            return aiServiceRestClient.post()
                    .uri(ANALYSIS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiAnalysisResponse.class);
        } catch (RestClientException e) {
            log.error("Falha ao chamar triaige-srv-ai: correlationId={}", request.getCorrelationId(), e);
            throw new AiAnalysisException("Falha ao chamar o serviço de IA (triaige-srv-ai)", e);
        }
    }
}
