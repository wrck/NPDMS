package com.dp.deviceops.adapter.web;

import com.dp.deviceops.parser.semantic.internal.CanonicalJson;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

final class CollectionRequestFingerprint {
    private CollectionRequestFingerprint() { }

    static String generic(String subject, GenericCollectionController.Request request) {
        return digest(values("version", 1, "subject", subject, "route", "generic", "namespace", request.namespace(),
                "context", request.context(), "connection", connection(request.connection()), "script", request.script(),
                "externalRequestId", request.externalRequestId(), "activityType", request.activityType(),
                "callbackUrl", request.callbackUrl(), "commandTimeoutSeconds", request.commandTimeoutSeconds(),
                "parseTimeoutSeconds", request.parseTimeoutSeconds(), "leaseGraceSeconds", request.leaseGraceSeconds(),
                "semanticParsing", request.semanticParsing()));
    }

    static String project(String subject, String projectKey, CollectionController.Request request) {
        return digest(values("version", 1, "subject", subject, "route", "project", "projectKey", projectKey,
                "namespace", request.namespace(), "project", request.project(), "targets", request.targets().stream()
                        .map(target -> values("project", target.project(), "device", target.device(),
                                "extensions", target.extensions(), "connection", connection(target.toConnection()))).toList(),
                "script", request.script(), "externalRequestId", request.externalRequestId(),
                "activityType", request.activityType(), "callbackUrl", request.callbackUrl(),
                "commandTimeoutSeconds", request.commandTimeoutSeconds(), "parseTimeoutSeconds", request.parseTimeoutSeconds(),
                "leaseGraceSeconds", request.leaseGraceSeconds(), "semanticParsing", request.semanticParsing()));
    }

    private static Map<String, Object> connection(ConnectionRequestMapper.Connection connection) {
        // Credentials never participate: replay must not persist secret hashes or require loading saved secrets.
        var result = values("protocol", connection.protocol(), "host", connection.host(), "port", connection.port(),
                "username", connection.username(), "authenticationType", connection.authenticationType(),
                "executionMode", connection.executionMode(), "hostKeyFingerprint", connection.hostKeyFingerprint(),
                "telnetPrompts", connection.telnetPrompts(), "connectTimeoutSeconds", connection.connectTimeoutSeconds(),
                "credentialNamespace", connection.credentialNamespace(), "savedConnectionId", connection.savedConnectionId(),
                "credentialId", connection.credentialId());
        if (connection.savedConnectionVersion() != null) result.put("savedConnectionVersion", connection.savedConnectionVersion());
        return result;
    }

    private static Map<String, Object> values(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put((String) values[index], values[index + 1]);
        }
        return map;
    }

    private static String digest(Map<String, Object> request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new CanonicalJson().bytes(request)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
