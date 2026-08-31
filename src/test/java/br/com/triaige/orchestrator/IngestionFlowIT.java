package br.com.triaige.orchestrator;

import br.com.triaige.orchestrator.api.dto.request.CreateSessionRequest;
import br.com.triaige.orchestrator.api.dto.request.PresignDocumentRequest;
import br.com.triaige.orchestrator.api.dto.response.CompleteDocumentResponse;
import br.com.triaige.orchestrator.api.dto.response.CreateSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.FinalizeSessionResponse;
import br.com.triaige.orchestrator.api.dto.response.PresignDocumentResponse;
import br.com.triaige.orchestrator.api.dto.response.SessionDetailResponse;
import br.com.triaige.orchestrator.domain.enums.DocumentStatus;
import br.com.triaige.orchestrator.domain.enums.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fluxo ponta a ponta: createSession -> presign -> upload S3 -> complete -> finalize -> get. */
class IngestionFlowIT extends AbstractIntegrationTest {

    private static final String FILE_CONTENT = "conteudo de teste do documento";

    @Test
    void fullIngestionFlow_endToEnd() throws Exception {
        UUID sessionId = createSession("Caso de teste", "CIVEL", "INDENIZACAO");

        PresignDocumentResponse presign = presignDocument(sessionId);
        assertThat(presign.getDocumentId()).isNotNull();
        assertThat(presign.getUploadUrl()).isNotBlank();

        uploadToPresignedUrl(presign.getUploadUrl(), "text/plain");

        CompleteDocumentResponse complete = completeDocument(sessionId, presign.getDocumentId());
        assertThat(complete.getStatus()).isEqualTo(DocumentStatus.RECEIVED);
        assertThat(complete.getTamanhoBytes()).isEqualTo(FILE_CONTENT.getBytes(StandardCharsets.UTF_8).length);

        FinalizeSessionResponse finalizeResponse = finalizeSession(sessionId);
        assertThat(finalizeResponse.getStatus()).isEqualTo(SessionStatus.QUEUED_FOR_PROCESSING);
        assertThat(finalizeResponse.getDocumentsCount()).isEqualTo(1);

        SessionDetailResponse detail = getSession(sessionId);
        assertThat(detail.getStatus()).isEqualTo(SessionStatus.QUEUED_FOR_PROCESSING);
        assertThat(detail.getDocuments()).hasSize(1);
        assertThat(detail.getDocuments().get(0).getStatus()).isEqualTo(DocumentStatus.QUEUED);
    }

    @Test
    void createSession_replayWithSameIdempotencyKey_returnsOriginalResponse() {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateSessionRequest request = buildCreateSessionRequest("Caso idempotente", "TRABALHISTA", "RESCISAO");

        ResponseEntity<CreateSessionResponse> first = postSessions(request, idempotencyKey);
        ResponseEntity<CreateSessionResponse> second = postSessions(request, idempotencyKey);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().getSessionId()).isEqualTo(first.getBody().getSessionId());
        assertThat(second.getBody().getProtocolo()).isEqualTo(first.getBody().getProtocolo());
    }

    @Test
    void createSession_replayWithSameKeyDifferentPayload_returnsConflict() {
        String idempotencyKey = UUID.randomUUID().toString();
        postSessions(buildCreateSessionRequest("Caso A", "CIVEL", "CONTRATO"), idempotencyKey);

        ResponseEntity<CreateSessionResponse> conflict =
                postSessions(buildCreateSessionRequest("Caso B", "CIVEL", "CONTRATO"), idempotencyKey);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void finalizeSession_withoutReceivedDocuments_returnsConflict() {
        UUID sessionId = createSession("Sem documentos", "CIVEL", "GENERICO");

        HttpHeaders headers = authHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/finalize", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("NO_DOCUMENTS_RECEIVED");
    }

    private UUID createSession(String titulo, String areaJuridica, String tipoCaso) {
        ResponseEntity<CreateSessionResponse> response = postSessions(
                buildCreateSessionRequest(titulo, areaJuridica, tipoCaso), UUID.randomUUID().toString());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getSessionId();
    }

    private CreateSessionRequest buildCreateSessionRequest(String titulo, String areaJuridica, String tipoCaso) {
        CreateSessionRequest.CasoRequest caso = new CreateSessionRequest.CasoRequest();
        caso.setTitulo(titulo);
        caso.setAreaJuridica(areaJuridica);
        caso.setTipoCaso(tipoCaso);
        CreateSessionRequest request = new CreateSessionRequest();
        request.setCaso(caso);
        return request;
    }

    private ResponseEntity<CreateSessionResponse> postSessions(CreateSessionRequest request, String idempotencyKey) {
        HttpHeaders headers = authHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(baseUrl() + "/sessions", HttpMethod.POST,
                new HttpEntity<>(request, headers), CreateSessionResponse.class);
    }

    private PresignDocumentResponse presignDocument(UUID sessionId) {
        PresignDocumentRequest request = new PresignDocumentRequest();
        request.setNomeArquivoOriginal("peticao.txt");
        request.setTipoDocumento("PETICAO_INICIAL");
        request.setContentType("text/plain");
        request.setTamanhoBytesEstimado((long) FILE_CONTENT.getBytes(StandardCharsets.UTF_8).length);

        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<PresignDocumentResponse> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/documents/presign", HttpMethod.POST,
                new HttpEntity<>(request, headers), PresignDocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void uploadToPresignedUrl(String uploadUrl, String contentType) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofString(FILE_CONTENT, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isBetween(200, 299);
    }

    private CompleteDocumentResponse completeDocument(UUID sessionId, UUID documentId) {
        HttpHeaders headers = authHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<CompleteDocumentResponse> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/documents/" + documentId + "/complete",
                HttpMethod.POST, new HttpEntity<>(headers), CompleteDocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private FinalizeSessionResponse finalizeSession(UUID sessionId) {
        HttpHeaders headers = authHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        ResponseEntity<FinalizeSessionResponse> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId + "/finalize", HttpMethod.POST,
                new HttpEntity<>(headers), FinalizeSessionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private SessionDetailResponse getSession(UUID sessionId) {
        HttpHeaders headers = authHeaders();
        ResponseEntity<SessionDetailResponse> response = restTemplate.exchange(
                baseUrl() + "/sessions/" + sessionId, HttpMethod.GET,
                new HttpEntity<>(headers), SessionDetailResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TEST_TOKEN);
        return headers;
    }
}
