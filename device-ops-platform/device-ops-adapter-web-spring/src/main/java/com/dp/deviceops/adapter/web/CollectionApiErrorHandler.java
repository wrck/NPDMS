package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.parser.CollectionSemanticResultController;
import com.dp.deviceops.core.model.ScriptArtifact;
import com.dp.deviceops.core.service.CollectionIdempotencyConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {GenericCollectionController.class, CollectionController.class,
        CollectionSemanticResultController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CollectionApiErrorHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<Error> invalid(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "collection request is invalid", request);
    }

    @ExceptionHandler(CollectionIdempotencyConflictException.class)
    ResponseEntity<Error> idempotency(CollectionIdempotencyConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "request key is already used by a different submission", request);
    }

    @ExceptionHandler(ScriptArtifact.DomainConflictException.class)
    ResponseEntity<Error> scriptConflict(ScriptArtifact.DomainConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "SCRIPT_VERSION_CONFLICT", "registered script version cannot change", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> status(ResponseStatusException exception, HttpServletRequest request) {
        String code = switch (exception.getStatusCode().value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 429 -> "QUEUE_FULL";
            default -> "COLLECTION_REQUEST_FAILED";
        };
        return response(exception.getStatusCode(), code, "collection request could not be completed", request);
    }

    private ResponseEntity<Error> response(HttpStatusCode status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Error(code, message, request.getRequestId()));
    }

    public record Error(String code, String message, String traceId) { }
}
