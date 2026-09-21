package com.dp.deviceops.parser.semantic.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveValueRedactorTest {

    @Test
    void oldConstructorAndExplicitFalseRetainLegacyOutput() {
        String input = "  Password: SYNTHETIC_FIRST SYNTHETIC_SECOND\n"
                + "  -----BEGIN PRIVATE KEY-----\n    SYNTHETIC_PAYLOAD\n  -----END PRIVATE KEY-----";
        String expected = "  Password: [REDACTED] SYNTHETIC_SECOND\n"
                + "  -----BEGIN PRIVATE KEY-----\n    SYNTHETIC_PAYLOAD\n  -----END PRIVATE KEY-----";

        assertEquals(expected, new SensitiveValueRedactor().redactText(input));
        assertEquals(expected, new SensitiveValueRedactor(false).redactText(input));
        assertEquals(new SensitiveValueRedactor().sanitize(List.of(input)),
                new SensitiveValueRedactor(false).sanitize(List.of(input)));
    }

    @Test
    void enhancedSemanticSanitizeRemovesAllUnquotedSuffixTokensRecursively() {
        var result = new SensitiveValueRedactor(true).sanitize(Map.of("facts", List.of(
                "Password: SYNTHETIC_FIRST SYNTHETIC_SECOND",
                Map.of("message", "credential = SYNTHETIC_THIRD SYNTHETIC_LAST"))));

        assertEquals(Map.of("facts", List.of("Password: [REDACTED]",
                Map.of("message", "credential = [REDACTED]"))), result.value());
        assertTrue(result.redacted());
    }

    @Test
    void enhancedTextSanitizeRetainsLineEndingsAndIndentationAcrossPrivateKeyBlocks() {
        String input = "before: retained\r\n  -----BEGIN EC PRIVATE KEY-----\r\n"
                + "    SYNTHETIC_PAYLOAD\n  -----END EC PRIVATE KEY-----\r\nafter: retained\r\n";

        var result = new SensitiveValueRedactor(true).sanitize(input);

        assertEquals("before: retained\r\n  [REDACTED]\r\n    [REDACTED]\n"
                + "  [REDACTED]\r\nafter: retained\r\n", result.value());
        assertTrue(result.redacted());
    }

    @Test
    void enhancedTextSanitizeRedactsUnterminatedPrivateKeyThroughEndOfText() {
        var result = new SensitiveValueRedactor(true).sanitize(
                "  -----BEGIN OPENSSH PRIVATE KEY-----\n    SYNTHETIC_UNTERMINATED\n\tSYNTHETIC_LAST");

        assertEquals("  [REDACTED]\n    [REDACTED]\n\t[REDACTED]", result.value());
        assertTrue(result.redacted());
    }

    @Test
    void enhancedTextSanitizeDoesNotJoinSensitiveKeyAndNextIndentedLine() {
        var result = new SensitiveValueRedactor(true).sanitize(
                "  passphrase:\r\n    SYNTHETIC_FIRST SYNTHETIC_SECOND\r\n  State: retained");

        assertEquals("  passphrase:\r\n    [REDACTED]\r\n  State: retained", result.value());
        assertTrue(result.redacted());
    }

    @Test
    void enhancedSemanticSanitizeCarriesPrivateKeyContextAcrossFactLineLists() {
        var result = new SensitiveValueRedactor(true).sanitize(Map.of("lines", List.of(
                "  -----BEGIN PRIVATE KEY-----", "    SYNTHETIC_PAYLOAD", "  -----END PRIVATE KEY-----",
                "  credential", "    SYNTHETIC_FIRST SYNTHETIC_SECOND", "State: retained")));

        assertEquals(Map.of("lines", List.of("  [REDACTED]", "    [REDACTED]", "  [REDACTED]",
                "  credential", "    [REDACTED]", "State: retained")), result.value());
        assertTrue(result.redacted());
    }

    @Test
    void enhancedTextSanitizeKeepsQuotedFieldBoundariesAndNonSensitiveText() {
        SensitiveValueRedactor redactor = new SensitiveValueRedactor(true);

        assertEquals("  Password: [REDACTED]  State: healthy",
                redactor.redactText("  Password: \"SYNTHETIC_FIRST SYNTHETIC_LAST\"  State: healthy"));
        assertEquals("password cipher [REDACTED] privilege 15",
                redactor.redactText("password cipher 'SYNTHETIC_FIRST SYNTHETIC_LAST' privilege 15"));
        assertEquals("http://example.invalid:80  IPv6: 2001:db8::1",
                redactor.redactText("http://example.invalid:80  IPv6: 2001:db8::1"));
        assertEquals("", redactor.redactText(null));
        assertFalse(redactor.sanitize("State: healthy").redacted());
    }
}
