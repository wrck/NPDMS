package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {ParserReleaseController.class, ParseTaskController.class})
public final class ParserApiErrorHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ParserApiError> invalid(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "request is invalid", request);
    }

    @ExceptionHandler(ParserApiNotFound.class)
    ResponseEntity<ParserApiError> notFound(ParserApiNotFound error, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, error.code, "resource was not found", request);
    }

    @ExceptionHandler(ParserRuntimeError.class)
    ResponseEntity<ParserApiError> runtime(ParserRuntimeError error, HttpServletRequest request) {
        HttpStatus status = switch (error.code()) {
            case "RELEASE_NOT_FOUND", "ACTIVE_RELEASE_NOT_FOUND", "TASK_NOT_FOUND", "RESULT_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "INPUT_TOO_LARGE" -> HttpStatus.PAYLOAD_TOO_LARGE;
            case "IDEMPOTENCY_CONFLICT", "RESULT_NOT_READY", "VERSION_ACTIVATION_CONFLICT", "RELEASE_STATE_CONFLICT",
                    "RELEASE_IMMUTABLE", "RELEASE_NOT_PUBLISHED", "TASK_NOT_RETRYABLE",
                    "TASK_NOT_CANCELLABLE", "TASK_NOT_WAITING" -> HttpStatus.CONFLICT;
            case "ARTIFACT_UNAVAILABLE", "TRANSIENT_STORAGE_ERROR" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return response(status, error.code(), error.getMessage(), request);
    }

    @ExceptionHandler(SemanticParserError.class)
    ResponseEntity<ParserApiError> bundle(SemanticParserError error, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, error.code(), error.getMessage(), request);
    }

    private static ResponseEntity<ParserApiError> response(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ParserApiError(
                code, message, Map.of(), request.getRequestId()));
    }

    public record ParserApiError(String code, String message, Map<String, Object> details, String traceId) { }
}

final class ParserApiNotFound extends RuntimeException {
    final String code;
    ParserApiNotFound(String code) {
        super(code);
        this.code = code;
    }
}
