package br.com.triaige.orchestrator.shared.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Métricas, namespace CloudWatch Triaige/Orchestrator (via management.cloudwatch.metrics.export). */
@Component
@RequiredArgsConstructor
public class IngestionMetrics {

    private final MeterRegistry registry;

    public void recordIngestionLatency(String endpoint, long millis) {
        registry.timer("IngestionLatencyMs", "endpoint", endpoint).record(Duration.ofMillis(millis));
    }

    public void incrementSessionsCreated() {
        registry.counter("SessionsCreatedCount").increment();
    }

    public void incrementDocumentsReceived() {
        registry.counter("DocumentsReceivedCount").increment();
    }

    public void incrementSessionsFinalized() {
        registry.counter("SessionsFinalizedCount").increment();
    }

    public void incrementFinalizeError(String errorCode) {
        registry.counter("FinalizeErrorCount", "code", errorCode).increment();
    }

    public void recordQ1PublishLatency(long millis) {
        registry.timer("Q1PublishLatencyMs").record(Duration.ofMillis(millis));
    }

    public void recordQ2PublishLatency(long millis) {
        registry.timer("Q2PublishLatencyMs").record(Duration.ofMillis(millis));
    }

    public void recordS3HeadObjectLatency(long millis) {
        registry.timer("S3HeadObjectLatencyMs").record(Duration.ofMillis(millis));
    }
}
