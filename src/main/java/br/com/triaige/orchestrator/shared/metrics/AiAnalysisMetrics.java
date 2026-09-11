package br.com.triaige.orchestrator.shared.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Métricas, mesmo namespace CloudWatch Triaige/Orchestrator. */
@Component
@RequiredArgsConstructor
public class AiAnalysisMetrics {

    private final MeterRegistry registry;

    public void recordTriggerLatency(long millis) {
        registry.timer("AiAnalysisTriggerLatencyMs").record(Duration.ofMillis(millis));
    }

    public void incrementSuccess() {
        registry.counter("AiAnalysisSuccessCount").increment();
    }

    public void incrementFailure(String errorCode) {
        registry.counter("AiAnalysisFailureCount", "error", errorCode).increment();
    }

    public void incrementRetry() {
        registry.counter("AiAnalysisRetryCount").increment();
    }

    public void incrementRenderFailure() {
        registry.counter("CuratedReportRenderFailureCount").increment();
    }
}
