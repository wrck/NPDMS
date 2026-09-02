package cn.iocoder.yudao.module.pms.service.domain.inspectionrule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InspectionRuleSecretScanner {

    private static final Pattern PRIVATE_KEY_HEADER = Pattern.compile(
            "-----BEGIN (?:RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----|-----BEGIN ENCRYPTED PRIVATE KEY-----");
    private static final Pattern AUTHORIZATION_HEADER = Pattern.compile(
            "^\\s*(?:Authorization|Proxy-Authorization)\\s*:\\s*\\S+",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern URL_CREDENTIAL = Pattern.compile(
            "\\b[a-z][a-z0-9+.-]*://[^\\s/@:]+:[^\\s/@]+@",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PASSWORD_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(?:password|passwd|pwd|passphrase)\\s*[=:]\\s*([^\\r\\n]+)");
    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
            "${NAME}", "{{NAME}}", "<PASSWORD>", "***", "REDACTED");

    public List<InspectionRuleRevisionRules.ValidationError> scan(
            InspectionRuleRevisionRules.RevisionDefinition revision) {
        List<InspectionRuleRevisionRules.ValidationError> errors = new ArrayList<>();
        scan(errors, "detectionId", revision.detectionId());
        scan(errors, "ruleName", revision.ruleName());
        scan(errors, "inspectionItem", revision.inspectionItem());
        scan(errors, "description", revision.description());
        for (int index = 0; index < revision.commands().size(); index++) {
            InspectionRuleRevisionRules.CommandDefinition command = revision.commands().get(index);
            if (command != null) {
                scan(errors, "commands[" + index + "].stableCommandKey", command.stableCommandKey());
                scan(errors, "commands[" + index + "].content", command.content());
            }
        }
        scan(errors, "expectedResultRegex", revision.expectedResultRegex());
        if (revision.threshold() != null) {
            scan(errors, "threshold.dataType", revision.threshold().dataType());
            scan(errors, "threshold.unit", revision.threshold().unit());
        }
        for (int index = 0; index < revision.productTypes().size(); index++) {
            scan(errors, "productTypes[" + index + "]", revision.productTypes().get(index));
        }
        return List.copyOf(errors);
    }

    private static void scan(
            List<InspectionRuleRevisionRules.ValidationError> errors,
            String location,
            String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (PRIVATE_KEY_HEADER.matcher(value).find()
                || AUTHORIZATION_HEADER.matcher(value).find()
                || URL_CREDENTIAL.matcher(value).find()
                || containsPasswordAssignment(value)) {
            errors.add(new InspectionRuleRevisionRules.ValidationError(
                    location,
                    "SECRET_DETECTED",
                    "SECRET_DETECTED"));
        }
    }

    private static boolean containsPasswordAssignment(String value) {
        Matcher matcher = PASSWORD_ASSIGNMENT.matcher(value);
        while (matcher.find()) {
            String assignedValue = stripPairedQuotes(matcher.group(1).trim());
            if (!assignedValue.isEmpty() && !ALLOWED_PLACEHOLDERS.contains(assignedValue)) {
                return true;
            }
        }
        return false;
    }

    private static String stripPairedQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }
}
