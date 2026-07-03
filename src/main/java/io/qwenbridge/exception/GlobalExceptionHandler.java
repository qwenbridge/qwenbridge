package io.qwenbridge.exception;

import lombok.extern.slf4j.Slf4j;
import io.qwenbridge.api.header.ApiHeaders;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.execution.provider.exception.SearchProviderException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        return buildError(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                message.isBlank() ? "Validation failed" : message,
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining(", "));

        return buildError(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                message.isBlank() ? "Validation failed" : message,
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                "Malformed JSON request body",
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.BAD_REQUEST,
                ErrorCode.BAD_REQUEST,
                safeMessage(exception, "Bad request"),
                request
        );
    }

    @ExceptionHandler(AIException.class)
    ResponseEntity<ApiError> handleAIProvider(
            AIException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.BAD_GATEWAY,
                ErrorCode.AI_PROVIDER_ERROR,
                safeMessage(exception, "AI provider failure"),
                request
        );
    }

    @ExceptionHandler(SearchProviderException.class)
    ResponseEntity<ApiError> handleSearchProvider(
            SearchProviderException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.BAD_GATEWAY,
                ErrorCode.SEARCH_PROVIDER_ERROR,
                safeMessage(exception, "Search provider failure"),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected API error on path {}", request.getRequestURI(), exception);

        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_ERROR,
                "Unexpected server error",
                request
        );
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request
    ) {
        ApiError error = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code.name())
                .message(message)
                .path(request.getRequestURI())
                .requestId(requestId(request))
                .build();

        return ResponseEntity.status(status).body(error);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + " " + violation.getMessage();
    }

    private String safeMessage(Exception exception, String fallback) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return fallback;
        }

        return exception.getMessage();
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(ApiHeaders.REQUEST_ID);

        if (requestId instanceof String value && !value.isBlank()) {
            return value;
        }

        String headerValue = request.getHeader(ApiHeaders.REQUEST_ID);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }

        return "";
    }
}
