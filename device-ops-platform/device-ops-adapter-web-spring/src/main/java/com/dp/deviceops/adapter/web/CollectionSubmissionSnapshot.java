package com.dp.deviceops.adapter.web;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** Streaming allowlist: excluded scalar values (especially credentials) are never read as Strings. */
final class CollectionSubmissionSnapshot {
    static final String ATTRIBUTE = CollectionSubmissionSnapshot.class.getName();
    static final String BUFFER_ATTRIBUTE = ATTRIBUTE + ".buffer";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ROOT = Set.of("namespace", "project", "context", "connection", "targets", "script", "externalRequestId", "activityType", "commandTimeoutSeconds", "parseTimeoutSeconds", "leaseGraceSeconds", "semanticParsing");
    private static final Set<String> CONNECTION = Set.of("protocol", "host", "port", "username", "authenticationType", "executionMode", "hostKeyFingerprint", "connectTimeoutSeconds", "credentialNamespace", "savedConnectionId", "credentialId");
    private static Set<String> fields(String kind) {
        return switch (kind) {
            case "body" -> ROOT;
            case "connection" -> CONNECTION;
            case "targets" -> { var s = new HashSet<>(CONNECTION); s.addAll(Set.of("project", "device")); yield s; }
            case "context" -> Set.of("project", "device");
            case "project" -> Set.of("namespace", "projectKey", "projectName", "projectCode");
            case "device" -> Set.of("deviceKey", "deviceName", "vendor", "model");
            case "script" -> Set.of("source", "key", "version", "sha256", "policy", "parserType");
            case "semanticParsing" -> Set.of("enabled", "logType", "releaseId", "inputFormat", "resultConsumerId");
            default -> Set.of();
        };
    }
    static String capture(byte[] body, String path, String idempotencyKey) throws IOException {
        var omitted = new ArrayList<String>();
        try (var parser = JSON.getFactory().createParser(body)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IOException("Invalid collection request");
            Object safeBody = read(parser, "body", "body", omitted);
            var envelope = new LinkedHashMap<String,Object>();
            envelope.put("schemaVersion", 1);
            var request = new LinkedHashMap<String,Object>();
            request.put("method", "POST"); request.put("path", path); request.put("idempotencyKey", idempotencyKey);
            envelope.put("request", request); envelope.put("body", safeBody); envelope.put("omittedFields", omitted);
            return JSON.writeValueAsString(envelope);
        } catch (IOException | RuntimeException invalid) {
            throw new IOException("Invalid collection request");
        }
    }
    private static Object read(JsonParser p, String kind, String path, List<String> omitted) throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) return null;
        boolean object = Set.of("body", "connection", "context", "project", "device", "script", "semanticParsing").contains(kind)
                || "targets".equals(kind) && path.endsWith("]");
        boolean array = "targets".equals(kind) && !path.endsWith("]");
        if (object && p.currentToken() != JsonToken.START_OBJECT || array && p.currentToken() != JsonToken.START_ARRAY
                || !object && !array && (p.currentToken() == JsonToken.START_OBJECT || p.currentToken() == JsonToken.START_ARRAY))
            throw new IOException("Invalid collection request");
        if (p.currentToken() == JsonToken.START_OBJECT) {
            var safe = new LinkedHashMap<String,Object>();
            while (p.nextToken() != JsonToken.END_OBJECT) {
                if (p.currentToken() != JsonToken.FIELD_NAME) throw new IOException("Invalid collection request");
                String field = p.currentName(); p.nextToken();
                if (fields(kind).contains(field)) safe.put(field, read(p, field, path + "." + field, omitted));
                else {
                    String excluded = Set.of("password", "privateKey", "passphrase", "extensions", "callbackUrl", "parserConfig", "content", "telnetPrompts").contains(field) ? field : "*";
                    String omittedPath = path + "." + excluded;
                    if (!omitted.contains(omittedPath)) omitted.add(omittedPath);
                    p.skipChildren();
                }
            }
            return safe;
        }
        if (p.currentToken() == JsonToken.START_ARRAY) {
            var list = new ArrayList<Object>();
            while (p.nextToken() != JsonToken.END_ARRAY) list.add(read(p, kind, path + "[" + list.size() + "]", omitted));
            return list;
        }
        return switch (p.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NUMBER_INT -> p.getLongValue();
            case VALUE_NUMBER_FLOAT -> p.getDecimalValue();
            case VALUE_STRING -> p.getText();
            default -> throw new IOException("Invalid collection request");
        };
    }
    static String current() {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : (String) attributes.getAttribute(ATTRIBUTE, 0);
    }
}
