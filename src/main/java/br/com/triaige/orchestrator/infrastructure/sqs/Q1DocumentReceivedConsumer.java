package br.com.triaige.orchestrator.infrastructure.sqs;

import br.com.triaige.orchestrator.application.service.DocumentReceivedFinalizationService;
import br.com.triaige.orchestrator.infrastructure.config.AwsProperties;
import br.com.triaige.orchestrator.infrastructure.sqs.message.DocumentReceivedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Consumidor interno de Q1: reprocessa idempotentemente
 * (por documentId) a mesma lógica do passo 4 de POST .../complete, garantindo
 * que uma falha na escrita síncrona do MySQL seja recuperada sem exigir novo
 * upload do arquivo. Mesma instância do Orchestrator — não há outro consumidor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Q1DocumentReceivedConsumer {

    private final SqsClient sqsClient;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final DocumentReceivedFinalizationService finalizationService;

    @Scheduled(fixedDelayString = "${aws.sqs.q1-consumer.poll-interval-ms:5000}")
    public void poll() {
        if (!awsProperties.getSqs().getQ1Consumer().isEnabled()) {
            return;
        }

        String queueUrl = awsProperties.getSqs().getDocsReceivedQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(awsProperties.getSqs().getQ1Consumer().getMaxMessages())
                        .waitTimeSeconds(awsProperties.getSqs().getQ1Consumer().getWaitTimeSeconds())
                        .build())
                .messages();

        for (Message message : messages) {
            processMessage(message, queueUrl);
        }
    }

    private void processMessage(Message message, String queueUrl) {
        try {
            DocumentReceivedMessage event = objectMapper.readValue(message.body(), DocumentReceivedMessage.class);
            finalizationService.apply(event.getDocumentId(), event.getSessionId(),
                    event.getTamanhoBytes(), event.getCorrelationId());
            deleteMessage(queueUrl, message);
        } catch (Exception e) {
            log.error("Failed to process Q1 message, will retry on next poll (or DLQ after maxReceiveCount): {}",
                    message.messageId(), e);
        }
    }

    private void deleteMessage(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
