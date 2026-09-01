package cn.iocoder.yudao.module.pms.service.domain.inspectionrule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionRuleSecretScannerTest {

    private final InspectionRuleSecretScanner scanner = new InspectionRuleSecretScanner();

    @Test
    void shouldAcceptOrdinaryTextAndApprovedPasswordPlaceholders() {
        for (String placeholder : List.of("${NAME}", "{{NAME}}", "<PASSWORD>", "***", "REDACTED")) {
            assertTrue(scanner.scan(revision("password = '" + placeholder + "'", "show status", "OK")).isEmpty());
        }
        assertTrue(scanner.scan(revision("Rule", "show status", "^(OK|WARN)$")).isEmpty());
    }

    @Test
    void shouldDetectApprovedSecretPatternsWithoutExposingMatchedText() {
        for (String header : List.of(
                "-----BEGIN PRIVATE KEY-----",
                "-----BEGIN RSA PRIVATE KEY-----",
                "-----BEGIN EC PRIVATE KEY-----",
                "-----BEGIN DSA PRIVATE KEY-----",
                "-----BEGIN OPENSSH PRIVATE KEY-----",
                "-----BEGIN ENCRYPTED PRIVATE KEY-----")) {
            assertSecret("ruleName", revision(header, "show status", "OK"));
        }
        for (String header : List.of(
                "Authorization: Bearer secret-value",
                "Proxy-Authorization: Basic secret-value")) {
            assertSecret("commands[0].content", revision("Rule", header, "OK"));
        }
        assertSecret("commands[0].content", revision(
                "Rule",
                "connect ssh://admin:secret@example.com/path",
                "OK"));
        for (String key : List.of("password", "passwd", "pwd", "passphrase")) {
            assertSecret("expectedResultRegex", revision(
                    "Rule",
                    "show status",
                    key + ": secret-value"));
        }
    }

    @Test
    void shouldScanAllApprovedRevisionTextLocations() {
        InspectionRuleRevisionRules.RevisionDefinition revision =
                new InspectionRuleRevisionRules.RevisionDefinition(
                        "DRAFT",
                        "password=secret",
                        "passwd: secret",
                        "pwd=secret",
                        "passphrase=secret",
                        "BASIC",
                        "GENERAL",
                        1,
                        List.of(new InspectionRuleRevisionRules.CommandDefinition(
                                "password=secret", "passphrase: secret", 1, 30, false)),
                        "password=secret",
                        new InspectionRuleRevisionRules.ThresholdDefinition(
                                "passwd=secret", "≤", new BigDecimal("80"), "pwd=secret"),
                        List.of("password=secret"));

        assertEquals(
                List.of(
                        "detectionId:SECRET_DETECTED",
                        "ruleName:SECRET_DETECTED",
                        "inspectionItem:SECRET_DETECTED",
                        "description:SECRET_DETECTED",
                        "commands[0].stableCommandKey:SECRET_DETECTED",
                        "commands[0].content:SECRET_DETECTED",
                        "expectedResultRegex:SECRET_DETECTED",
                        "threshold.dataType:SECRET_DETECTED",
                        "threshold.unit:SECRET_DETECTED",
                        "productTypes[0]:SECRET_DETECTED"),
                locationsAndCodes(scanner.scan(revision)));
    }

    private static void assertSecret(
            String expectedLocation,
            InspectionRuleRevisionRules.RevisionDefinition revision) {
        List<InspectionRuleRevisionRules.ValidationError> errors =
                new InspectionRuleSecretScanner().scan(revision);
        assertEquals(1, errors.size());
        assertEquals(expectedLocation, errors.getFirst().location());
        assertEquals("SECRET_DETECTED", errors.getFirst().code());
        assertEquals("SECRET_DETECTED", errors.getFirst().message());
    }

    private static InspectionRuleRevisionRules.RevisionDefinition revision(
            String ruleName,
            String command,
            String expectedResultRegex) {
        return new InspectionRuleRevisionRules.RevisionDefinition(
                "DRAFT",
                "DET-1",
                ruleName,
                "Item",
                "Description",
                "BASIC",
                "GENERAL",
                1,
                List.of(new InspectionRuleRevisionRules.CommandDefinition(
                        "CMD-1", command, 1, 30, false)),
                expectedResultRegex,
                new InspectionRuleRevisionRules.ThresholdDefinition(
                        "NUMBER", "≤", new BigDecimal("80"), "%"),
                List.of("TYPE-A"));
    }

    private static List<String> locationsAndCodes(
            List<InspectionRuleRevisionRules.ValidationError> errors) {
        return errors.stream()
                .map(error -> error.location() + ":" + error.code())
                .toList();
    }
}
