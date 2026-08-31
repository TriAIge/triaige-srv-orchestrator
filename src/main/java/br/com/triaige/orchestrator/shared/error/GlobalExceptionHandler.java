package br.com.triaige.orchestrator.shared.error;

import br.com.triaige.orchestrator.domain.exception.OrchestratorException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrchestratorException.class)
    public ResponseEntity<ErrorResponse> handleOrchestratorException(OrchestratorException ex,
                                                                       HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{}: {} - path={}", ex.getCode(), ex.getMessage(), request.getRequestURI(), ex);
        } else {
            log.warn("{}: {} - path={}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        }
        return buildResponse(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {} - path={}", message, request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(Exception ex, HttpServletRequest request) {
        log.warn("Missing request data: {} - path={}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno inesperado", request);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message,
                                                          HttpServletRequest request) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .code(code)
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
