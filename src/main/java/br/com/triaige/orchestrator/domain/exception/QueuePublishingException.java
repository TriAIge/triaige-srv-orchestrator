package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatus;

public class QueuePublishingException extends OrchestratorException {

    public QueuePublishingException(String queueUrl, Throwable cause) {
        super("QUEUE_PUBLISH_ERROR", HttpStatus.INTERNAL_SERVER_ERROR,
                "Falha ao publicar mensagem na fila: " + queueUrl, cause);
    }
}
