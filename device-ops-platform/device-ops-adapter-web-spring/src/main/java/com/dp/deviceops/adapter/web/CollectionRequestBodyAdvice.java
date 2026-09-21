package com.dp.deviceops.adapter.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;

/** Registers credentials only after deserialization; the filter owns their request-lifetime cleanup. */
@ControllerAdvice(assignableTypes = {
        CollectionController.class, GenericCollectionController.class, ConnectionTestController.class,
        CredentialController.class, SavedConnectionController.class
})
public final class CollectionRequestBodyAdvice implements RequestBodyAdvice {
    private final HttpServletRequest request;
    public CollectionRequestBodyAdvice(HttpServletRequest request) { this.request=request; }
    @Override public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converterType) {
        return type == CollectionController.Request.class || type == GenericCollectionController.Request.class
                || type == ConnectionRequestMapper.Connection.class || type == CredentialController.Request.class
                || type == SavedConnectionController.CreateRequest.class
                || type == SavedConnectionController.ReplaceRequest.class;
    }
    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converterType) throws java.io.IOException {
        if (type != CollectionController.Request.class && type != GenericCollectionController.Request.class) return input;
        // A bounded, wipeable replay buffer preserves the normal sensitive-char-array deserializer.
        byte[] bytes = input.getBody().readNBytes(4 * 1024 * 1024 + 1);
        request.setAttribute(CollectionSubmissionSnapshot.BUFFER_ATTRIBUTE, bytes);
        if (bytes.length > 4 * 1024 * 1024) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "collection request is too large");
        }
        try {
            request.setAttribute(CollectionSubmissionSnapshot.ATTRIBUTE,
                    CollectionSubmissionSnapshot.capture(bytes, request.getRequestURI(), request.getHeader("Idempotency-Key")));
        } catch (java.io.IOException invalid) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new org.springframework.http.converter.HttpMessageNotReadableException("Invalid collection request", input);
        }
        return new HttpInputMessage() {
            public org.springframework.http.HttpHeaders getHeaders() { return input.getHeaders(); }
            public java.io.InputStream getBody() { return new java.io.ByteArrayInputStream(bytes); }
        };
    }
    @Override public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converterType) { request.setAttribute(CollectionCredentialCleanupFilter.ATTRIBUTE, body); return body; }
    @Override public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converterType) { return body; }
}
