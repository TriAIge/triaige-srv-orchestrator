package br.com.triaige.orchestrator.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Payload do callback do triaige-srv-notification ao concluir a notificação de uma sessão. */
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
