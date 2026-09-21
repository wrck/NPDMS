package com.dp.deviceops.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/** Owns every credential array decoded during one HTTP request until the cleanup filter clears it. */
public final class RequestSensitiveCharArrayRegistry {
    static final String ATTRIBUTE = RequestSensitiveCharArrayRegistry.class.getName() + ".arrays";

    private RequestSensitiveCharArrayRegistry() {
    }

    public static void register(char[] value) {
        if (value == null) {
            return;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            @SuppressWarnings("unchecked")
            List<char[]> values = (List<char[]>) request.getAttribute(ATTRIBUTE);
            if (values == null) {
                values = new ArrayList<>();
                request.setAttribute(ATTRIBUTE, values);
            }
            values.add(value);
        }
    }

    public static void clear(HttpServletRequest request) {
        Object registered = request.getAttribute(ATTRIBUTE);
        if (registered instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof char[] sensitive) {
                    ConnectionRequestMapper.clear(sensitive);
                }
            }
        }
        request.removeAttribute(ATTRIBUTE);
    }
}
