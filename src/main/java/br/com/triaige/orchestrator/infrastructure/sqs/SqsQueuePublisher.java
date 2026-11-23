package br.com.triaige.orchestrator.infrastructure.sqs;

import br.com.triaige.orchestrator.domain.exception.QueuePublishingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsQueuePublisher implements QueuePublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String queueUrl, Object payload) {
        try {
            String messageBody = objectMapper.writeValueAsString(payload);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);

            log.info("Mensagem publicada: queue={}, messageId={}", queueUrl, response.messageId());
        } catch (Exception e) {
            log.error("Falha ao publicar mensagem na fila: {}", queueUrl, e);
            throw new QueuePublishingException(queueUrl, e);
        }
    }
}
