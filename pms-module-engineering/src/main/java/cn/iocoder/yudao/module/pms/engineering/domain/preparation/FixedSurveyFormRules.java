package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_FIXED_FORM_CATALOG_INVALID;

public final class FixedSurveyFormRules {

    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "NUMBER", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT");
    private static final Set<String> FROZEN_ROOT_FIELDS = Set.of("schemaVersion", "formCode", "formVersion", "fields");

    private FixedSurveyFormRules() {
    }

    public static String freeze(Integer schemaVersion, FixedSurveyFormCatalog.FormDefinition form,
                                List<FixedSurveyFormCatalog.FieldDefinition> fields) {
        if (!Integer.valueOf(1).equals(schemaVersion) || form == null || fields == null) {
            throw exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
        }
        List<FixedSurveyFormCatalog.FieldDefinition> orderedFields = fields.stream()
                .sorted(Comparator.comparing(FixedSurveyFormCatalog.FieldDefinition::sortOrder)
                        .thenComparing(FixedSurveyFormCatalog.FieldDefinition::fieldCode))
                .toList();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("schemaVersion", schemaVersion);
        schema.put("formCode", form.formCode());
        schema.put("formVersion", form.formVersion());
        schema.put("fields", orderedFields);
        return JsonUtils.toJsonString(schema);
    }

    public static FrozenSchema parseFrozen(String schemaSnapshot) {
        try {
            JsonNode root = JsonUtils.parseTree(schemaSnapshot);
            if (root == null || !root.isObject()) throw invalid();
            Set<String> actual = new HashSet<>();
            root.properties().forEach(entry -> actual.add(entry.getKey()));
            if (!FROZEN_ROOT_FIELDS.equals(actual)) throw invalid();
            FrozenSchema schema = JsonUtils.parseObject(schemaSnapshot, FrozenSchema.class);
            if (schema == null || !Integer.valueOf(1).equals(schema.schemaVersion())
                    || schema.formCode() == null || schema.formCode().isBlank()
                    || !Integer.valueOf(1).equals(schema.formVersion())
                    || schema.fields() == null || schema.fields().isEmpty()) throw invalid();
            JsonNode fieldsNode = root.get("fields");
            if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.size() != schema.fields().size()) throw invalid();
            Set<String> codes = new HashSet<>();
            Set<Integer> orders = new HashSet<>();
            Set<String> allowedFieldProperties = Set.of(
                    "fieldCode", "fieldType", "required", "maxLength", "options", "sortOrder");
            for (int index = 0; index < schema.fields().size(); index++) {
                Set<String> fieldProperties = new HashSet<>();
                fieldsNode.get(index).properties().forEach(entry -> fieldProperties.add(entry.getKey()));
                if (!allowedFieldProperties.containsAll(fieldProperties)) throw invalid();
                FixedSurveyFormCatalog.FieldDefinition field = schema.fields().get(index);
                validateDefinition(field);
                if (!codes.add(field.fieldCode()) || !orders.add(field.sortOrder())) throw invalid();
            }
            return schema;
        } catch (RuntimeException ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) throw ex;
            throw invalid();
        }
    }

    public static String validateAndNormalizeValue(String schemaSnapshot, String valueSnapshot) {
        FrozenSchema schema = parseFrozen(schemaSnapshot);
        JsonNode valueRoot;
        try {
            valueRoot = JsonUtils.parseTree(valueSnapshot);
        } catch (RuntimeException ex) {
            throw invalid();
        }
        if (valueRoot == null || !valueRoot.isObject()) throw invalid();
        Set<String> allowedCodes = schema.fields().stream()
                .map(FixedSurveyFormCatalog.FieldDefinition::fieldCode).collect(java.util.stream.Collectors.toSet());
        Map<String, JsonNode> submitted = new LinkedHashMap<>();
        valueRoot.properties().forEach(entry -> submitted.put(entry.getKey(), entry.getValue()));
        if (!allowedCodes.containsAll(submitted.keySet())) throw invalid();

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (FixedSurveyFormCatalog.FieldDefinition field : schema.fields().stream()
                .sorted(Comparator.comparing(FixedSurveyFormCatalog.FieldDefinition::sortOrder)
                        .thenComparing(FixedSurveyFormCatalog.FieldDefinition::fieldCode)).toList()) {
            JsonNode value = submitted.get(field.fieldCode());
            if ((value == null || value.isNull()) && Boolean.TRUE.equals(field.required())) throw invalid();
            if (value == null || value.isNull()) continue;
            normalized.put(field.fieldCode(), normalizeField(field, value));
        }
        return JsonUtils.toJsonString(normalized);
    }

    static void validateDefinition(FixedSurveyFormCatalog.FieldDefinition field) {
        if (field == null || field.fieldCode() == null || field.fieldCode().isBlank()
                || !FIELD_TYPES.contains(field.fieldType()) || field.required() == null
                || field.sortOrder() == null || field.sortOrder() < 0) throw invalid();
        boolean selection = "SINGLE_SELECT".equals(field.fieldType()) || "MULTI_SELECT".equals(field.fieldType());
        if (selection) {
            if (field.options() == null || field.options().isEmpty()
                    || field.options().stream().anyMatch(option -> option == null || option.isBlank())
                    || new HashSet<>(field.options()).size() != field.options().size()) throw invalid();
        } else if (field.options() != null && !field.options().isEmpty()) {
            throw invalid();
        }
        if ("TEXT".equals(field.fieldType())) {
            if (field.maxLength() == null || field.maxLength() <= 0 || field.maxLength() > 4000) throw invalid();
        } else if (field.maxLength() != null) {
            throw invalid();
        }
    }

    private static Object normalizeField(FixedSurveyFormCatalog.FieldDefinition field, JsonNode value) {
        return switch (field.fieldType()) {
            case "TEXT" -> {
                if (!value.isString() || value.asText().length() > field.maxLength()
                        || Boolean.TRUE.equals(field.required()) && value.asText().isBlank()) throw invalid();
                yield value.asText();
            }
            case "NUMBER" -> {
                if (!value.isNumber()) throw invalid();
                yield new BigDecimal(value.asText()).stripTrailingZeros();
            }
            case "BOOLEAN" -> {
                if (!value.isBoolean()) throw invalid();
                yield value.asBoolean();
            }
            case "SINGLE_SELECT" -> {
                if (!value.isString() || !field.options().contains(value.asText())) throw invalid();
                yield value.asText();
            }
            case "MULTI_SELECT" -> {
                if (!value.isArray()) throw invalid();
                List<String> selected = new ArrayList<>();
                for (JsonNode option : value) {
                    if (!option.isString() || !field.options().contains(option.asText()) || selected.contains(option.asText())) {
                        throw invalid();
                    }
                    selected.add(option.asText());
                }
                if (Boolean.TRUE.equals(field.required()) && selected.isEmpty()) throw invalid();
                yield selected;
            }
            default -> throw invalid();
        };
    }

    private static cn.iocoder.yudao.framework.common.exception.ServiceException invalid() {
        return exception(PREPARATION_FIXED_FORM_CATALOG_INVALID);
    }

    public record FrozenSchema(Integer schemaVersion, String formCode, Integer formVersion,
                               List<FixedSurveyFormCatalog.FieldDefinition> fields) {
    }
}
