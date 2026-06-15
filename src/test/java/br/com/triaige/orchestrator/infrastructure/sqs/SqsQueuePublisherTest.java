package br.com.triaige.orchestrator.infrastructure.sqs;

import br.com.triaige.orchestrator.domain.exception.QueuePublishingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqsQueuePublisher")
class SqsQueuePublisherTest {

    @Mock private SqsClient sqsClient;

    private SqsQueuePublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new SqsQueuePublisher(sqsClient, objectMapper);
    }

    @Test
    @DisplayName("deve serializar payload e publicar na fila correta")
    void shouldSerializeAndPublishMessage() {
        // Arrange
        String queueUrl = "http://localhost:4566/000000000000/triaige-docs-preprocessing";
        UUID lawFirmId = UUID.randomUUID();
        PreProcessingRequestedMessage payload = PreProcessingRequestedMessage.builder()
                .eventType("PRE_PROCESSING_REQUESTED")
                .sessionId(UUID.randomUUID())
                .lawFirmId(lawFirmId)
                .correlationId(UUID.randomUUID())
                .protocolo("TRI-2026-000001")
                .build();

        SendMessageResponse mockResponse = SendMessageResponse.builder()
                .messageId("msg-123")
                .build();
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(mockResponse);

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);

        // Act
        publisher.publish(queueUrl, payload);

        // Assert
        verify(sqsClient).sendMessage(captor.capture());
        SendMessageRequest sent = captor.getValue();

        assertThat(sent.queueUrl()).isEqualTo(queueUrl);
        assertThat(sent.messageBody()).contains("PRE_PROCESSING_REQUESTED");
        assertThat(sent.messageBody()).contains(lawFirmId.toString());
        // NÃO deve conter "null" para campos obrigatórios
        assertThat(sent.messageBody()).doesNotContain("\"eventType\":null");
    }

    @Test
    @DisplayName("deve lançar QueuePublishingException quando SQS falha")
    void shouldThrowQueuePublishingExceptionOnSqsFailure() {
        // Arrange
        String queueUrl = "http://localhost:4566/000000000000/triaige-docs-preprocessing";
        lenient().when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("Connection refused").build());

        // Act & Assert
        assertThatThrownBy(() -> publisher.publish(queueUrl, new Object()))
                .isInstanceOf(QueuePublishingException.class)
                .hasMessageContaining(queueUrl);
    }
}
