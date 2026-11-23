package br.com.triaige.orchestrator.domain.event;

import br.com.triaige.orchestrator.api.dto.request.McpResultCallbackRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publicado ao aplicar um mcp-result COMPLETED ou PARTIALLY_COMPLETED. Só tipos simples, nunca
 * entidades: o listener roda em outra thread, fora da sessão do Hibernate.
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
