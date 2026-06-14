package br.com.triaige.orchestrator.infrastructure.sqs;

public interface QueuePublisher {

    /**
     * Publica uma mensagem na fila SQS especificada.
     * O payload será serializado para JSON antes do envio.
     *
     * @param queueUrl a URL completa da fila SQS
     * @param payload  o objeto a ser serializado e enviado como corpo da mensagem
     * @throws br.com.triaige.orchestrator.domain.exception.QueuePublishingException se a publicação falhar
     */
    void publish(String queueUrl, Object payload);
}
