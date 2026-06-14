package br.com.triaige.orchestrator.domain.exception;

public class QueuePublishingException extends RuntimeException {
    public QueuePublishingException(String queueUrl, Throwable cause) {
        super("Falha ao publicar mensagem na fila: " + queueUrl, cause);
    }
}
