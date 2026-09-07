package br.com.triaige.orchestrator.infrastructure.http;

import br.com.triaige.orchestrator.domain.exception.McpAiHttpException;
import br.com.triaige.orchestrator.infrastructure.config.OrchestratorProperties;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisRequestDto;
import br.com.triaige.orchestrator.infrastructure.http.dto.AnalysisResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Cobre a distinção rede-vs-HTTP-explícito da spec Fase 4, seção 2.4: falha de rede chega
 * como {@link ResourceAccessException} (única categoria elegível a retry, decidido pelo
 * chamador — {@code TriggerAiAnalysisUseCase}); erro HTTP explícito vira
 * {@link McpAiHttpException}, nunca elegível a retry.
 */
class McpAiAnalysisClientTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    private OrchestratorProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OrchestratorProperties();
        properties.getMcpAi().setAnalyzeToken("test-token");
    }

    private AnalysisRequestDto buildRequest() {
        return AnalysisRequestDto.builder()
                .sessionId(SESSION_ID)
                .correlationId(UUID.randomUUID())
                .protocolo("PROTO-001")
                .legalCase(AnalysisRequestDto.LegalCaseDto.builder()
                        .id(UUID.randomUUID()).titulo("t").areaJuridica("civel").tipoCaso("cobranca").build())
                .build();
    }

    @Test
    void success_returnsParsedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://mcp-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpAiAnalysisClient client = new McpAiAnalysisClient(builder.build(), properties);

        server.expect(requestTo("http://mcp-ai.test/api/ai/v1/analyze"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(header("Idempotency-Key", SESSION_ID.toString()))
                .andRespond(withSuccess("""
                        { "schemaVersion": "2.0", "sessionId": "%s", "status": "COMPLETED" }
                        """.formatted(SESSION_ID), MediaType.APPLICATION_JSON));

        AnalysisResponseDto response = client.analyze(buildRequest(), SESSION_ID);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        server.verify();
    }

    @Test
    void explicitHttpError_throwsMcpAiHttpException_withStatusPreserved() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://mcp-ai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpAiAnalysisClient client = new McpAiAnalysisClient(builder.build(), properties);

        server.expect(requestTo("http://mcp-ai.test/api/ai/v1/analyze"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY)
                        .body("{\"error\":\"LLM_UNAVAILABLE\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.analyze(buildRequest(), SESSION_ID))
                .isInstanceOf(McpAiHttpException.class)
                .satisfies(e -> assertThat(((McpAiHttpException) e).getStatus().value()).isEqualTo(502));
    }

    @Test
    void connectionFailure_throwsResourceAccessException() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        RestClient realClient = RestClient.builder()
                .baseUrl("http://localhost:" + closedPort)
                .requestFactory(factory)
                .build();
        McpAiAnalysisClient client = new McpAiAnalysisClient(realClient, properties);

        assertThatThrownBy(() -> client.analyze(buildRequest(), SESSION_ID))
                .isInstanceOf(ResourceAccessException.class);
    }
}
