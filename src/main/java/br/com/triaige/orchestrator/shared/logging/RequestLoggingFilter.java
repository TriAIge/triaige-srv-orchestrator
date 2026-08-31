package br.com.triaige.orchestrator.shared.logging;

import br.com.triaige.orchestrator.shared.metrics.IngestionMetrics;
import br.com.triaige.orchestrator.shared.util.CorrelationIdUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Log estruturado por requisição (spec seção 9): um evento JSON com
 * timestamp, level, service, endpoint, method, correlationId, sessionId
 * (quando aplicável), statusCode e latencyMs — nunca conteúdo de documentos
 * ou dados pessoais. correlationId/sessionId ficam no MDC para que qualquer
 * log emitido durante a requisição (inclusive de outras camadas) já os inclua.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final Pattern SESSION_ID_PATTERN =
            Pattern.compile("/sessions/([0-9a-fA-F-]{36})");

    private final IngestionMetrics ingestionMetrics;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        UUID correlationId = CorrelationIdUtil.resolve(request.getHeader(CORRELATION_ID_HEADER));

        MDC.put("correlationId", correlationId.toString());
        MDC.put("endpoint", request.getRequestURI());
        MDC.put("method", request.getMethod());
        putSessionIdIfPresent(request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long latencyMs = System.currentTimeMillis() - startedAt;
            MDC.put("statusCode", String.valueOf(response.getStatus()));
            MDC.put("latencyMs", String.valueOf(latencyMs));
            MDC.put("event", "request_completed");

            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), latencyMs);

            ingestionMetrics.recordIngestionLatency(request.getRequestURI(), latencyMs);

            MDC.clear();
        }
    }

    private void putSessionIdIfPresent(String uri) {
        Matcher matcher = SESSION_ID_PATTERN.matcher(uri);
        if (matcher.find()) {
            MDC.put("sessionId", matcher.group(1));
        }
    }
}
