package br.com.triaige.orchestrator.domain.event;

import br.com.triaige.orchestrator.api.dto.request.McpResultCallbackRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publicado por {@code ApplyMcpResultUseCase} ao final de sua transação (Fase 4, spec seção
 * 2.2) quando o {@code mcp-result} recebido tem status COMPLETED/PARTIALLY_COMPLETED.
 *
 * <p>Deliberadamente um {@code record} com apenas tipos primitivos/DTOs — NUNCA entidades
 * JPA. O listener consome este evento numa thread {@code @Async}, fora da
 * transação/sessão Hibernate original; {@code TriageSession.legalCase} é {@code LAZY} e
 * acessá-lo depois do commit lançaria {@code LazyInitializationException}. Os dados
 * necessários são copiados para tipos simples ainda dentro da transação original, em
 * {@code ApplyMcpResultUseCase}.</p>
 */
public record AiAnalysisTriggeredEvent(
        UUID sessionId,
        UUID lawFirmId,
        UUID correlationId,
        String protocolo,
        UUID legalCaseId,
        String areaJuridica,
        String tipoCaso,
        String titulo,
        LocalDateTime sessionCreatedAt,
        LocalDateTime triggeredAt,
        List<McpResultCallbackRequest.ProcessedGroup> processedGroups,
        List<McpResultCallbackRequest.FailedDocument> failedDocuments) {
}
