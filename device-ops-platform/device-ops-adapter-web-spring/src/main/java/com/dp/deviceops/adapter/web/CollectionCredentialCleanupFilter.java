package com.dp.deviceops.adapter.web;

import jakarta.servlet.*; import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Clears decoded write-only arrays even if validation, authorization, or controller execution rejects the request. */
public final class CollectionCredentialCleanupFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE=CollectionCredentialCleanupFilter.class.getName()+".request";
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        try {
            chain.doFilter(request,response);
        } finally {
            Object capturedBuffer = request.getAttribute(CollectionSubmissionSnapshot.BUFFER_ATTRIBUTE);
            if (capturedBuffer instanceof byte[] bytes) java.util.Arrays.fill(bytes, (byte) 0);
            request.removeAttribute(CollectionSubmissionSnapshot.BUFFER_ATTRIBUTE);
            request.removeAttribute(CollectionSubmissionSnapshot.ATTRIBUTE);
            RequestSensitiveCharArrayRegistry.clear(request);
            Object body=request.getAttribute(ATTRIBUTE);
            if(body instanceof CollectionController.Request collection) collection.clearCredentials();
            if(body instanceof GenericCollectionController.Request collection) collection.clearCredentials();
            if(body instanceof ConnectionRequestMapper.Connection connection) connection.clearCredentials();
            if(body instanceof CredentialController.Request credential) credential.clear();
            if(body instanceof SavedConnectionController.CreateRequest savedConnection) savedConnection.clear();
            if(body instanceof SavedConnectionController.ReplaceRequest savedConnection) savedConnection.clear();
        }
    }
}
