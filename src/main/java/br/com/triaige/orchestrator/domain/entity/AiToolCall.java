package br.com.triaige.orchestrator.domain.entity;

import br.com.triaige.orchestrator.domain.converter.AiToolNameConverter;
import br.com.triaige.orchestrator.domain.enums.AiToolCallStatus;
import br.com.triaige.orchestrator.domain.enums.AiToolName;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapeia a tabela {@code ai_tool_calls} do PRÓPRIO Orchestrator (schema espelhado — o
 * mcp-ai tem sua cópia independente da mesma tabela, em outro banco). Usada nesta fase
 * apenas para gravar um registro-stub que satisfaz {@code fk_triage_results_jurisprudence_call}
 * quando {@code jurisprudenceCallId} vem do mcp-ai (gap documentado — o UUID de fato foi
 * gerado na tabela ai_tool_calls do mcp-ai, não nesta).
 * {@code requestPayload}/{@code responsePayload} ficam sempre nulos aqui — nunca replicar
 * texto/PII entre serviços.
 */
@Entity
@Table(name = "ai_tool_calls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiToolCall {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID sessionId;

    @Convert(converter = AiToolNameConverter.class)
    @Column(name = "tool_name", nullable = false, length = 80)
    private AiToolName toolName;

    @Column(name = "provider", length = 80)
    private String provider;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private AiToolCallStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
