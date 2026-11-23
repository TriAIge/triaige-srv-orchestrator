package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatusCode;

/** Erro HTTP devolvido pelo mcp-ai. Não é repetido: o mcp-ai já esgotou as próprias tentativas. */
public class McpAiHttpException extends RuntimeException {

    private final HttpStatusCode status;
    private final String responseBody;

    public McpAiHttpException(HttpStatusCode status, String responseBody) {
        super("mcp-ai respondeu " + status + ": " + responseBody);
        this.status = status;
        this.responseBody = responseBody;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
