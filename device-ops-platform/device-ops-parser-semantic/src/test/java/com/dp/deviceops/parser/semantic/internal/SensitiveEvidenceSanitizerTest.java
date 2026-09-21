package com.dp.deviceops.parser.semantic.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class SensitiveEvidenceSanitizerTest {

    @Test
    void oldConstructorAndExplicitFalseRetainLegacyEvidenceOutput() {
        NormalizedEvidenceUnit input = unit(List.of("  Password: SYNTHETIC_FIRST SYNTHETIC_SECOND",
                "  -----BEGIN PRIVATE KEY-----", "    SYNTHETIC_PAYLOAD", "  -----END PRIVATE KEY-----"),
                List.of("credential: SYNTHETIC_FIRST SYNTHETIC_SECOND"));

        var legacy = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor()).sanitize(List.of(input));
        var explicitFalse = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor(false), false)
                .sanitize(List.of(input));

        assertEquals(legacy, explicitFalse);
        assertEquals(List.of("  Password: [REDACTED] SYNTHETIC_SECOND", "  -----BEGIN PRIVATE KEY-----",
                "    SYNTHETIC_PAYLOAD", "  -----END PRIVATE KEY-----"), legacy.units().getFirst().contentLines());
        assertEquals(List.of("credential: [REDACTED] SYNTHETIC_SECOND"), legacy.units().getFirst().errorLines());
        assertEquals(2, legacy.redactedValueCount());
    }

    @Test
    void enhancedCopyRemovesWholeMultiwordValuesAndRetainsCommandModifiers() {
        NormalizedEvidenceUnit original = unit(List.of(
                "  Password: SYNTHETIC_FIRST SYNTHETIC_SECOND SYNTHETIC_LAST",
                "\tpassphrase = \"SYNTHETIC_QUOTED FIRST LAST\"",
                " password encrypted-password cipher SYNTHETIC_COMMAND FIRST LAST",
                " snmp-agent community read cipher SYNTHETIC_COMMUNITY FIRST LAST",
                " State: healthy"), List.of("  credential: SYNTHETIC_ERROR FIRST LAST"));
        SensitiveEvidenceSanitizer sanitizer = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor(), true);

        SensitiveEvidenceSanitizer.Result result = sanitizer.sanitize(List.of(original));

        assertEquals(List.of("  Password: [REDACTED]", "\tpassphrase = [REDACTED]",
                " password encrypted-password cipher [REDACTED]",
                " snmp-agent community read cipher [REDACTED]", " State: healthy"),
                result.units().getFirst().contentLines());
        assertEquals(List.of("  credential: [REDACTED]"), result.units().getFirst().errorLines());
        assertEquals(5, result.redactedValueCount());
        assertEquals(Set.of(1), result.changedCommandIndexes());
        assertEquals(original.contentLines().size(), result.units().getFirst().contentLines().size());
        assertSame(original.provenance(), result.units().getFirst().provenance());
        assertEquals(result.units(), sanitizer.sanitize(result.units()).units());
        assertEquals(0, sanitizer.sanitize(result.units()).redactedValueCount());
    }

    @Test
    void enhancedCopyRetainsIndentedSensitiveKeysAndRedactsTheirContinuationValues() {
        NormalizedEvidenceUnit original = unit(List.of(
                "  Password:", "", "    SYNTHETIC_FIRST SYNTHETIC_SECOND",
                "      SYNTHETIC_CONTINUATION", "  State: healthy",
                "\tprivate_key =", "\t\tSYNTHETIC_PRIVATE_VALUE", "outside: retained"), List.of());

        var result = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor(), true).sanitize(List.of(original));

        assertEquals(List.of("  Password:", "", "    [REDACTED]", "      [REDACTED]",
                "  State: healthy", "\tprivate_key =", "\t\t[REDACTED]", "outside: retained"),
                result.units().getFirst().contentLines());
    }

    @Test
    void enhancedCopyRedactsCompleteAndIncompletePemBlocksWithoutChangingLineShape() {
        NormalizedEvidenceUnit original = unit(List.of(
                "before: retained", "  -----BEGIN RSA PRIVATE KEY-----",
                "    SYNTHETIC_PEM_FIRST", "\tSYNTHETIC_PEM_SECOND", "  ",
                "  -----END RSA PRIVATE KEY-----", "after: retained"),
                List.of("\t-----BEGIN ENCRYPTED PRIVATE KEY-----", "\t\tSYNTHETIC_INCOMPLETE", ""));
        SensitiveEvidenceSanitizer sanitizer = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor(), true);

        var result = sanitizer.sanitize(List.of(original));

        assertEquals(List.of("before: retained", "  [REDACTED]", "    [REDACTED]",
                "\t[REDACTED]", "  ", "  [REDACTED]", "after: retained"),
                result.units().getFirst().contentLines());
        assertEquals(List.of("\t[REDACTED]", "\t\t[REDACTED]", ""),
                result.units().getFirst().errorLines());
        assertEquals(result.units(), sanitizer.sanitize(result.units()).units());
        assertEquals(0, sanitizer.sanitize(result.units()).redactedValueCount());
    }

    @Test
    void enhancedPemStateDoesNotLeakAcrossContentErrorsOrEvidenceUnits() {
        NormalizedEvidenceUnit first = unit(List.of("-----BEGIN PRIVATE KEY-----", "SYNTHETIC_PAYLOAD"),
                List.of("error: retained"));
        NormalizedEvidenceUnit second = new NormalizedEvidenceUnit(2, "COMMAND_OUTPUT",
                Map.of("commandText", "show second"), List.of("State: retained"), List.of(), false);

        var result = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor(), true).sanitize(List.of(first, second));

        assertEquals(List.of("[REDACTED]", "[REDACTED]"), result.units().getFirst().contentLines());
        assertEquals(first.errorLines(), result.units().getFirst().errorLines());
        assertEquals(second, result.units().get(1));
        assertEquals(Set.of(1), result.changedCommandIndexes());
    }

    private static NormalizedEvidenceUnit unit(List<String> content, List<String> errors) {
        return new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT", Map.of("commandText", "show synthetic"),
                content, errors, false);
    }

    @Test
    void createsAnImmutableIdempotentSafeCopyForContentAndErrors() {
        EvidenceProvenance provenance = new EvidenceProvenance(
                3, "show aggregate", 1, "show secrets", 2, 40);
        NormalizedEvidenceUnit original = new NormalizedEvidenceUnit(
                7,
                "COMMAND_OUTPUT",
                Map.of("commandText", "show secrets", "status", "SUCCEEDED"),
                List.of(
                        "password encrypted-password fixture-password-token",
                        "password cipher fixture-password-token",
                        "snmp-agent community read cipher fixture-community-token"),
                List.of("credential: fixture-password-token"),
                true,
                provenance);

        SensitiveEvidenceSanitizer sanitizer = new SensitiveEvidenceSanitizer(new SensitiveValueRedactor());
        SensitiveEvidenceSanitizer.Result first = sanitizer.sanitize(List.of(original));
        NormalizedEvidenceUnit safe = first.units().getFirst();

        assertEquals(List.of(
                "password encrypted-password [REDACTED]",
                "password cipher [REDACTED]",
                "snmp-agent community read cipher [REDACTED]"), safe.contentLines());
        assertEquals(List.of("credential: [REDACTED]"), safe.errorLines());
        assertFalse(String.join("\n", safe.contentLines()).contains("fixture-password-token"));
        assertFalse(String.join("\n", safe.contentLines()).contains("fixture-community-token"));
        assertEquals(4, first.redactedValueCount());
        assertEquals(Set.of(3), first.changedCommandIndexes());
        assertEquals("password encrypted-password fixture-password-token", original.contentLines().getFirst());
        assertSame(provenance, safe.provenance());
        assertEquals(original.attributes(), safe.attributes());
        assertEquals(original.truncated(), safe.truncated());

        SensitiveEvidenceSanitizer.Result second = sanitizer.sanitize(first.units());
        assertEquals(first.units(), second.units());
        assertEquals(0, second.redactedValueCount());
        assertEquals(Set.of(), second.changedCommandIndexes());
    }
}
