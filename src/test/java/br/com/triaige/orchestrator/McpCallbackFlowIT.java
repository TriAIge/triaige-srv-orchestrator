package br.com.triaige.orchestrator;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Callback do triaige-srv-mcp-ai (spec da Fase 2, seção 6) — endpoint novo desta entrega. */
class McpCallbackFlowIT extends AbstractIntegrationTest {

    private static final String VALID_INTERNAL_TOKEN = "dev-local-internal-token";

    @Test
    void mcpResult_withValidTokenAndCompletedStatus_updatesSessionStatus() {
        UUID sessionId = createSession();

        Map<String, Object> payload = Map.of(
                "schemaVersion", "1.0",
                "sessionId", sessionId.toString(),
                "correlationId", UUID.randomUUID().toString(),
                "status", "COMPLETED",
                "processedGroups", List.of(Map.of(
                        "attachmentGroupId", UUID.randomUUID().toString(),
                        "trustedBucket", "bucket-triaige-trusted-certificacoes",
                        "trustedObjectKey", "trusted/x/y/z.json",
                        "tipoDocumento", "PETICAO_INICIAL",
                        "resumido", false,
                        "piiRedactedCategories", List.of("CPF", "EMAIL"))),
                "failedDocuments", List.of(),
                "completedAt", LocalDateTime.now().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", VALID_INTERNAL_TOKEN);

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/mcp-result", HttpMethod.POST,
                new HttpEntity<>(payload, headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        SessionDetailResponse detail = getSession(sessionId);
        assertThat(detail.getStatus()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void mcpResult_withoutInternalToken_returnsUnauthorized() {
        UUID sessionId = createSession();

        Map<String, Object> payload = Map.of(
                "schemaVersion", "1.0",
                "sessionId", sessionId.toString(),
                "status", "FAILED",
                "processedGroups", List.of(),
                "failedDocuments", List.of(),
                "completedAt", LocalDateTime.now().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/mcp-result", HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void mcpResult_withWrongInternalToken_returnsUnauthorized() {
        UUID sessionId = createSession();

        Map<String, Object> payload = Map.of(
                "schemaVersion", "1.0",
                "sessionId", sessionId.toString(),
                "status", "FAILED",
                "processedGroups", List.of(),
                "failedDocuments", List.of(),
                "completedAt", LocalDateTime.now().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", "token-errado");

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/mcp-result", HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private UUID createSession() {
        CreateSessionRequest.CasoRequest caso = new CreateSessionRequest.CasoRequest();
        caso.setTitulo("Caso para callback do MCP");
        caso.setAreaJuridica("CIVEL");
        caso.setTipoCaso("INDENIZACAO");
        CreateSessionRequest request = new CreateSessionRequest();
        request.setCaso(caso);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TEST_TOKEN);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<CreateSessionResponse> response = restTemplate.exchange(
                baseUrl() + "/sessions", HttpMethod.POST, new HttpEntity<>(request, headers), CreateSessionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getSessionId();
    }

    private SessionDetailResponse getSession(UUID sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TEST_TOKEN);
        ResponseEntity<SessionDetailResponse> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId, HttpMethod.GET, new HttpEntity<>(headers), SessionDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
