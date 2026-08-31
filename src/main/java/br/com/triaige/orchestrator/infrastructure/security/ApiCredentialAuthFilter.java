package br.com.triaige.orchestrator.infrastructure.security;

import br.com.triaige.orchestrator.application.service.ApiCredentialAuthService;
import br.com.triaige.orchestrator.application.service.AuthenticatedCredential;
import br.com.triaige.orchestrator.domain.exception.OrchestratorException;
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
 * Autentica toda requisição sob {@code orchestrator.base-path} via Authorization: Bearer <token>
 * (spec seção 5). Em sucesso, expõe {@code lawFirmId} e {@code apiCredentialId} como request
 * attributes, consumidos pelos controllers via @RequestAttribute.
 */
@Component
@RequiredArgsConstructor
public class ApiCredentialAuthFilter extends OncePerRequestFilter {

    public static final String LAW_FIRM_ID_ATTRIBUTE = "lawFirmId";
    public static final String API_CREDENTIAL_ID_ATTRIBUTE = "apiCredentialId";

    private final ApiCredentialAuthService apiCredentialAuthService;
    private final OrchestratorProperties orchestratorProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.endsWith("/mcp-result")) {
            // Autenticado via X-Internal-Token por InternalTokenAuthFilter, não por Bearer.
            return true;
        }
        return !uri.startsWith(orchestratorProperties.getBasePath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            AuthenticatedCredential credential = apiCredentialAuthService.authenticate(
                    request.getHeader("Authorization"));
            request.setAttribute(LAW_FIRM_ID_ATTRIBUTE, credential.getLawFirmId());
            request.setAttribute(API_CREDENTIAL_ID_ATTRIBUTE, credential.getApiCredentialId());
            filterChain.doFilter(request, response);
        } catch (OrchestratorException ex) {
            writeError(response, request, ex);
        }
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request,
                             OrchestratorException ex) throws IOException {
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
