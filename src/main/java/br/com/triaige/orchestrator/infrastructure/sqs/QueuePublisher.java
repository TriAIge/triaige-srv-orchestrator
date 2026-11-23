package br.com.triaige.orchestrator.infrastructure.sqs;

public interface QueuePublisher {

    /**
     * Serializa o payload em JSON e publica na fila.
     *
     * @throws br.com.triaige.orchestrator.domain.exception.QueuePublishingException se a publicação falhar
     */
    void publish(String queueUrl, Object payload);
}
