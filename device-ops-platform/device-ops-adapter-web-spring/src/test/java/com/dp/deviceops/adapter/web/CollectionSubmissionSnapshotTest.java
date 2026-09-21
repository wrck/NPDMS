package com.dp.deviceops.adapter.web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSubmissionSnapshotTest {
    @Test void duplicateFieldsAreRejectedWithoutLeakingValues() {
        assertThrows(java.io.IOException.class, () -> CollectionSubmissionSnapshot.capture(
                "{\"namespace\":\"one\",\"namespace\":\"two\"}".getBytes(), "/api/v1/collections", "idem"));
    }
    @Test void unknownFieldNamesAndMalformedContainerValuesAreNotCaptured() throws Exception {
        String json = CollectionSubmissionSnapshot.capture("{\"SECRET_UNKNOWN_NAME\":\"SECRET_VALUE\"}".getBytes(), "/api/v1/collections", "idem");
        assertFalse(json.contains("SECRET"));
        var failure = assertThrows(java.io.IOException.class, () -> CollectionSubmissionSnapshot.capture(
                "{\"connection\":\"SECRET_WRONG_TYPE\"}".getBytes(), "/api/v1/collections", "idem"));
        assertFalse(failure.getMessage().contains("SECRET"));
        assertNull(failure.getCause());
    }
    @Test void actualSemanticSelectionFieldsPreserveAutomaticDisabledAndPinned() throws Exception {
        for (String selection : java.util.List.of("null", "{\"enabled\":false}", "{\"enabled\":true,\"logType\":\"syslog\",\"releaseId\":null,\"inputFormat\":\"command-output-block/v1\"}", "{\"enabled\":true,\"logType\":\"syslog\",\"releaseId\":\"release-1\",\"inputFormat\":\"command-output-block/v1\",\"resultConsumerId\":\"consumer\"}")) {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String body = "{\"semanticParsing\":" + selection + "}";
            var captured = mapper.readTree(CollectionSubmissionSnapshot.capture(body.getBytes(), "/api/v1/collections", "idem"));
            assertEquals(mapper.readTree(selection), captured.path("body").path("semanticParsing"));
        }
    }
    @Test void capturesOnlyAllowlistedActualBody() throws Exception {
        var type = assertDoesNotThrow(() -> Class.forName("com.dp.deviceops.adapter.web.CollectionSubmissionSnapshot"));
        var capture = type.getDeclaredMethod("capture", byte[].class, String.class, String.class);
        String body = """
                {"namespace":"owned","commandTimeoutSeconds":12,"semanticParsing":null,
                 "connection":{"host":"example.invalid","password":"SECRET_PASSWORD","privateKey":"SECRET_KEY","passphrase":"SECRET_PASS","savedConnectionId":"saved"},
                 "script":{"key":"script","content":"SCRIPT_BODY","parserConfig":"SECRET_CONFIG"},
                 "callbackUrl":"SECRET_CALLBACK","context":{"extensions":{"token":"SECRET_EXTENSION"}}}
                """;
        String json = (String) capture.invoke(null, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), "/api/v1/collections", "idem");
        assertFalse(json.contains("SECRET_"));
        assertFalse(json.contains("SCRIPT_BODY"));
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertEquals(1, tree.path("schemaVersion").asInt());
        assertEquals("idem", tree.path("request").path("idempotencyKey").asText());
        assertTrue(tree.path("body").get("semanticParsing").isNull());
        assertFalse(tree.path("body").has("parseTimeoutSeconds"));
        assertEquals(12, tree.path("body").path("commandTimeoutSeconds").asInt());
        assertEquals("saved", tree.path("body").path("connection").path("savedConnectionId").asText());
        assertTrue(json.contains("body.connection.password"));
        assertTrue(json.contains("body.script.content"));
    }
}
