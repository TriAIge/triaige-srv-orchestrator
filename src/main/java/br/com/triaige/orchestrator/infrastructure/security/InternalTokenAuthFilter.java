package br.com.triaige.orchestrator.infrastructure.security;

import br.com.triaige.orchestrator.domain.exception.UnauthorizedException;
import br.com.triaige.orchestrator.infrastructure.config.OrchestratorProperties;
import br.com.triaige.orchestrator.shared.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Autentica callbacks internos — do triaige-srv-mcp-ai e do
 * triaige-srv-notification — via {@code X-Internal-Token}, comparado ao shared secret
 * configurado para cada origem — não o esquema de Bearer token de {@link ApiCredentialAuthFilter},
 * que não se aplica a estes endpoints (não há {@code api_credentials} envolvida numa chamada
 * servidor-a-servidor interna). Em produção, os endpoints também não devem ficar expostos via
 * ALB/API Gateway (rede interna apenas) — isso é responsabilidade de infraestrutura, fora deste
 * código.
 */
@Component
@RequiredArgsConstructor
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private final OrchestratorProperties orchestratorProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.endsWith("/mcp-result") && !uri.endsWith("/notification-result");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("X-Internal-Token");
        String expected = request.getRequestURI().endsWith("/notification-result")
                ? orchestratorProperties.getInternal().getNotificationCallbackToken()
                : orchestratorProperties.getInternal().getMcpCallbackToken();

        if (token == null || expected == null || !constantTimeEquals(token, expected)) {
            writeError(response, request, new UnauthorizedException("X-Internal-Token inválido ou ausente"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                             UnauthorizedException ex) throws IOException {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatus().value())
                .code(ex.getCode())
                .error(ex.getStatus().getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
