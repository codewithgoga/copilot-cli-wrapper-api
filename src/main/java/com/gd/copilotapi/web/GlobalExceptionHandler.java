package com.gd.copilotapi.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.gd.copilotapi.copilot.CopilotAuthenticationException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CopilotAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleAuthFailure(CopilotAuthenticationException exception) {
        log.warn("Returning authentication error: {}", exception.getMessage());
        return errorBody(exception.getMessage(), "authentication_error");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String reason = exception.getReason();
        String type = switch (status) {
            case NOT_FOUND -> "not_found_error";
            case CONFLICT -> "conflict_error";
            case TOO_MANY_REQUESTS -> "rate_limit_error";
            case FORBIDDEN -> "permission_error";
            case NOT_IMPLEMENTED -> "not_implemented_error";
            default -> status.is4xxClientError() ? "invalid_request_error" : "server_error";
        };
        log.warn("Returning status error. status={}, message={}", exception.getStatusCode().value(), reason == null ? "" : reason);
        return ResponseEntity.status(status)
                .body(errorBody(reason, type));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, ConstraintViolationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(Exception exception) {
        log.warn("Returning bad request: {}", exception.getMessage());
        return errorBody(exception.getMessage(), "invalid_request_error");
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleCliFailure(IllegalStateException exception) {
        log.error("Returning upstream CLI error: {}", exception.getMessage());
        return errorBody(exception.getMessage(), "copilot_cli_error");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception exception) {
        log.error("Returning unexpected server error.", exception);
        return errorBody(exception.getMessage(), "server_error");
    }

    private Map<String, Object> errorBody(String message, String type) {
        return Map.of(
                "error", Map.of(
                        "message", message == null ? "Unexpected error." : message,
                        "type", type
                )
        );
    }
}
