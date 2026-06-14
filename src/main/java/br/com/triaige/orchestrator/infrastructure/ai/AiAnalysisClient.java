package br.com.triaige.orchestrator.infrastructure.ai;

public interface AiAnalysisClient {

    /**
     * Solicita a análise jurídica de IA de forma síncrona ao triaige-srv-ai.
     *
     * @throws br.com.triaige.orchestrator.domain.exception.AiAnalysisException se a chamada falhar
     */
    AiAnalysisResponse analyze(AiAnalysisRequest request);
}
