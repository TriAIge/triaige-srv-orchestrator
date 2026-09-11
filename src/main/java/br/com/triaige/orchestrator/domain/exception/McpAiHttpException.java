package br.com.triaige.orchestrator.domain.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Erro HTTP EXPLÍCITO devolvido pelo triaige-srv-mcp-ai (502/504/422/401/etc). Nunca elegível
 * ao retry único de rede: o mcp-ai já esgotou suas próprias
 * tentativas internas antes de responder com um status de erro.
 */
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
