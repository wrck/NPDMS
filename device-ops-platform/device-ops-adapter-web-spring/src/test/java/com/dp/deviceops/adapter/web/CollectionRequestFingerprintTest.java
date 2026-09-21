package com.dp.deviceops.adapter.web;

import com.dp.deviceops.core.model.ConnectionProtocol;
import com.dp.deviceops.core.port.CommandExecutionPort;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionRequestFingerprintTest {
    @Test
    void fingerprintExcludesSecretsButIncludesSubjectAndExecutionIntent() throws Exception {
        Class<?> fingerprints = Class.forName("com.dp.deviceops.adapter.web.CollectionRequestFingerprint");
        var method = fingerprints.getDeclaredMethod("generic", String.class, GenericCollectionController.Request.class);
        String original = (String) method.invoke(null, "service-a", request("secret", "show version", Map.of("b", "2", "a", "1")));
        String rotated = (String) method.invoke(null, "service-a", request(null, "show version", Map.of("a", "1", "b", "2")));
        assertEquals(original, rotated);
        assertTrue(original.matches("[0-9a-f]{64}"));
        assertNotEquals(original, method.invoke(null, "service-b", request("secret", "show version", Map.of("a", "1", "b", "2"))));
        assertNotEquals(original, method.invoke(null, "service-a", request("secret", "show clock", Map.of("a", "1", "b", "2"))));
    }

    @Test
    void canonicalRequestStillDistinguishesSavedSelectionAndDirectFields() throws Exception {
        Class<?> fingerprints = Class.forName("com.dp.deviceops.adapter.web.CollectionRequestFingerprint");
        var method = fingerprints.getDeclaredMethod("generic", String.class, GenericCollectionController.Request.class);
        var request = request("secret", "show version", Map.of());
        var saved = new ConnectionRequestMapper.Connection(null, null, null, null, null, null,
                null, null, null, null, "saved-1", null, null, null, null);
        var savedRequest = new GenericCollectionController.Request(request.namespace(), request.context(), saved,
                request.script(), request.externalRequestId(), request.activityType(), request.callbackUrl(),
                request.commandTimeoutSeconds(), request.parseTimeoutSeconds(), request.leaseGraceSeconds(), request.semanticParsing());
        assertNotEquals(method.invoke(null, "service", request), method.invoke(null, "service", savedRequest));
    }

    private GenericCollectionController.Request request(String password, String command, Map<String, String> extensions) {
        var connection = new ConnectionRequestMapper.Connection(ConnectionProtocol.SSH2, "10.0.0.10", 22, "operator",
                CommandExecutionPort.AuthenticationType.PASSWORD, CommandExecutionPort.ExecutionMode.EXEC,
                "SHA256:fixture", null, 10L, null, null, null,
                password == null ? null : password.toCharArray(), null, null);
        var script = new GenericCollectionController.Script("ADHOC_INLINE", "external", "1", command,
                "0".repeat(64), "EXECUTION_ONLY", "NONE", null);
        return new GenericCollectionController.Request("owned", new GenericCollectionController.Context(null, null, extensions),
                connection, script, null, null, null, 30, 5, 10, null);
    }
}
