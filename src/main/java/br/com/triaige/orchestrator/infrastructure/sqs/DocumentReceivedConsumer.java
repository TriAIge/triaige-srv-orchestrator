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
 * Consome a triaige-docs-received e reaplica, de forma idempotente, a confirmação do upload,
 * recuperando escritas que falharam no MySQL sem exigir novo upload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReceivedConsumer {

    private final SqsClient sqsClient;
    private final AwsProperties awsProperties;
    private final ObjectMapper objectMapper;
    private final DocumentReceivedFinalizationService finalizationService;

    @Scheduled(fixedDelayString = "${aws.sqs.document-received-consumer.poll-interval-ms:5000}")
    public void poll() {
        if (!awsProperties.getSqs().getDocumentReceivedConsumer().isEnabled()) {
            return;
        }

        String queueUrl = awsProperties.getSqs().getDocsReceivedQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            return;
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(awsProperties.getSqs().getDocumentReceivedConsumer().getMaxMessages())
                        .waitTimeSeconds(awsProperties.getSqs().getDocumentReceivedConsumer().getWaitTimeSeconds())
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
            log.error("Falha ao processar mensagem da triaige-docs-received; nova tentativa no próximo poll (ou DLQ após maxReceiveCount): {}",
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
