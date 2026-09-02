package cn.iocoder.yudao.module.pms.service.domain.inspectionrule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InspectionRuleRevisionRules {

    private static final Set<String> CATEGORY_CODES = Set.of(
            "BASIC", "OPERATING_STATUS", "LOG", "BUSINESS_STATUS", "REDUNDANCY",
            "ROUTING", "SECURITY", "FORWARDING_CHANNEL", "LOAD_BALANCING", "TRAFFIC_CLEANING");
    private static final Set<String> SEVERITY_CODES = Set.of("GENERAL", "SEVERE", "FATAL");
    private static final Set<String> THRESHOLD_OPERATORS = Set.of(">", "<", "≥", "≤", "=", "≠");

    private final InspectionRuleRegexValidator regexValidator = new InspectionRuleRegexValidator();
    private final InspectionRuleSecretScanner secretScanner = new InspectionRuleSecretScanner();

    public List<ValidationError> validate(RevisionDefinition revision) {
        List<ValidationError> errors = new ArrayList<>();
        if (!"DRAFT".equals(revision.status())) {
            errors.add(error("status", "NOT_EDITABLE"));
        }
        required(errors, "detectionId", revision.detectionId());
        required(errors, "ruleName", revision.ruleName());
        required(errors, "inspectionItem", revision.inspectionItem());
        required(errors, "description", revision.description());
        supported(errors, "categoryCode", revision.categoryCode(), CATEGORY_CODES);
        supported(errors, "severityCode", revision.severityCode(), SEVERITY_CODES);
        if (revision.sortOrder() == null) {
            errors.add(error("sortOrder", "REQUIRED"));
        }
        validateCommands(errors, revision.commands());
        errors.addAll(regexValidator.validate(revision.expectedResultRegex()));
        validateThreshold(errors, revision.threshold());
        validateProductTypes(errors, revision.productTypes());
        errors.addAll(secretScanner.scan(revision));
        return List.copyOf(errors);
    }

    private static void validateCommands(List<ValidationError> errors, List<CommandDefinition> commands) {
        if (commands == null || commands.isEmpty()) {
            errors.add(error("commands", "REQUIRED"));
            return;
        }
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (CommandDefinition command : commands) {
            if (command != null) {
                orders.add(command.executionOrder());
            }
        }
        for (int index = 0; index < commands.size(); index++) {
            CommandDefinition command = commands.get(index);
            String location = "commands[" + index + "]";
            if (command == null) {
                errors.add(error(location, "REQUIRED"));
                continue;
            }
            if (isBlank(command.stableCommandKey())) {
                errors.add(error(location + ".stableCommandKey", "REQUIRED"));
            } else if (!keys.add(command.stableCommandKey())) {
                errors.add(error(location + ".stableCommandKey", "DUPLICATE"));
            }
            required(errors, location + ".content", command.content());
            if (command.executionOrder() < 1
                    || command.executionOrder() > commands.size()
                    || orders.size() != commands.size()) {
                errors.add(error(location + ".executionOrder", "NOT_CONTINUOUS"));
            }
            if (command.timeoutSeconds() < 1 || command.timeoutSeconds() > 30) {
                errors.add(error(location + ".timeoutSeconds", "OUT_OF_RANGE"));
            }
        }
    }

    private static void validateThreshold(List<ValidationError> errors, ThresholdDefinition threshold) {
        if (threshold == null) {
            errors.add(error("threshold", "REQUIRED"));
            return;
        }
        if (isBlank(threshold.dataType())) {
            errors.add(error("threshold.dataType", "REQUIRED"));
        } else if (!"NUMBER".equals(threshold.dataType())) {
            errors.add(error("threshold.dataType", "UNSUPPORTED_VALUE"));
        }
        if (isBlank(threshold.operator()) || !THRESHOLD_OPERATORS.contains(threshold.operator())) {
            errors.add(error("threshold.operator", "INVALID"));
        }
        if (threshold.value() == null) {
            errors.add(error("threshold.value", "REQUIRED"));
        }
        required(errors, "threshold.unit", threshold.unit());
    }

    private static void validateProductTypes(List<ValidationError> errors, List<String> productTypes) {
        if (productTypes == null || productTypes.isEmpty()) {
            errors.add(error("productTypes", "REQUIRED"));
            return;
        }
        Set<String> codes = new HashSet<>();
        for (int index = 0; index < productTypes.size(); index++) {
            String productType = productTypes.get(index);
            if (isBlank(productType)) {
                errors.add(error("productTypes[" + index + "]", "REQUIRED"));
            } else if (!codes.add(productType)) {
                errors.add(error("productTypes[" + index + "]", "DUPLICATE"));
            }
        }
    }

    private static void required(List<ValidationError> errors, String location, String value) {
        if (isBlank(value)) {
            errors.add(error(location, "REQUIRED"));
        }
    }

    private static void supported(
            List<ValidationError> errors,
            String location,
            String value,
            Set<String> supportedValues) {
        if (isBlank(value)) {
            errors.add(error(location, "REQUIRED"));
        } else if (!supportedValues.contains(value)) {
            errors.add(error(location, "UNSUPPORTED_VALUE"));
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationError error(String location, String code) {
        return new ValidationError(location, code, code);
    }

    public record RevisionDefinition(
            String status,
            String detectionId,
            String ruleName,
            String inspectionItem,
            String description,
            String categoryCode,
            String severityCode,
            Integer sortOrder,
            List<CommandDefinition> commands,
            String expectedResultRegex,
            ThresholdDefinition threshold,
            List<String> productTypes) {

        public RevisionDefinition {
            commands = commands == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(commands));
            productTypes = productTypes == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(productTypes));
        }
    }

    public record CommandDefinition(
            String stableCommandKey,
            String content,
            int executionOrder,
            int timeoutSeconds,
            boolean continueOnTimeout) {
    }

    public record ThresholdDefinition(
            String dataType,
            String operator,
            BigDecimal value,
            String unit) {
    }

    public record ValidationError(String location, String code, String message) {
    }
}
