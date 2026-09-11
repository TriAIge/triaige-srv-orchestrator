package br.com.triaige.orchestrator.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Payload do callback síncrono do triaige-srv-notification ao concluir (com sucesso ou falha)
 * a notificação de todos os destinatários de uma sessão: POST
 * {basePath}/sessions/{sessionId}/notification-result, autenticado por X-Internal-Token
 * (rede interna), não pelo ApiCredentialAuthFilter de Bearer token usado pelo restante da API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResultCallbackRequest {

    private UUID sessionId;
    private UUID correlationId;
    /** NOTIFIED | NOTIFICATION_FAILED */
    private String status;
    private int recipientsNotified;
    private int recipientsFailed;
}
