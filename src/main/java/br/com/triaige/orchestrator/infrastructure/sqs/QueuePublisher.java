package br.com.triaige.orchestrator.infrastructure.sqs;

public interface QueuePublisher {

    /**
     * Publica uma mensagem na fila SQS especificada. O payload é serializado
     * para JSON antes do envio.
     *
     * @throws br.com.triaige.orchestrator.domain.exception.QueuePublishingException se a publicação falhar
     */
    void publish(String queueUrl, Object payload);
}
