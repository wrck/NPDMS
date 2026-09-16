package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityExtensionApi.Definition;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityField;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.ENTITY_VALUE_INVALID;

final class EntityExtensionValidation {
    private EntityExtensionValidation() {}

    static Definition fromForm(cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor field,
                               String code) {
        var type = switch (field.valueType()) {
            case "boolean" -> EntityField.Type.BOOLEAN;
            case "number" -> EntityField.Type.NUMBER;
            case "array" -> EntityField.Type.TEXT_LIST;
            case "any" -> EntityField.Type.TEXT;
            default -> throw exception(ENTITY_VALUE_INVALID);
        };
        return new Definition(code, code, type, field.required(), field.maxLength(), field.allowedValues());
    }

    static void definitions(List<Definition> fields, List<EntityField> fixed) {
        if (fields == null) throw exception(ENTITY_VALUE_INVALID);
        Set<String> codes = new HashSet<>();
        fixed.forEach(field -> codes.add(field.code()));
        for (Definition field : fields) {
            if (field == null || field.code() == null || field.code().isBlank() || !codes.add(field.code())
                    || field.label() == null || field.label().isBlank() || field.type() == null
                    || field.maxLength() != null && field.maxLength() < 0
                    || field.allowedValues() != null && (field.allowedValues().stream().anyMatch(Objects::isNull)
                    || new HashSet<>(field.allowedValues()).size() != field.allowedValues().size())) {
                throw exception(ENTITY_VALUE_INVALID);
            }
        }
    }

    static void values(List<Definition> definitions, Map<String, Object> values, boolean complete) {
        if (values == null) throw exception(ENTITY_VALUE_INVALID);
        Set<String> codes = new HashSet<>();
        for (Definition definition : definitions) {
            codes.add(definition.code());
            Object value = values.get(definition.code());
            if (value == null || value instanceof String text && text.isBlank()) {
                if (complete && definition.required()) throw exception(ENTITY_VALUE_INVALID);
                continue;
            }
            boolean valid = switch (definition.type()) {
                case TEXT -> value instanceof String;
                case NUMBER -> finiteNumber(value);
                case BOOLEAN -> value instanceof Boolean;
                case DATE -> date(value, false);
                case DATETIME -> date(value, true);
                case TEXT_LIST -> value instanceof List<?> list && list.stream().allMatch(String.class::isInstance);
                case OBJECT_LIST -> value instanceof List<?> list && list.stream().allMatch(Map.class::isInstance);
            };
            if (!valid || value instanceof String text && definition.maxLength() != null
                    && text.length() > definition.maxLength()) throw exception(ENTITY_VALUE_INVALID);
            if (definition.allowedValues() != null && !definition.allowedValues().isEmpty()) {
                if (value instanceof List<?> list) {
                    if (!definition.allowedValues().containsAll(list)) throw exception(ENTITY_VALUE_INVALID);
                } else if (!definition.allowedValues().contains(value)) throw exception(ENTITY_VALUE_INVALID);
            }
            if (complete && definition.required() && value instanceof List<?> list && list.isEmpty()) {
                throw exception(ENTITY_VALUE_INVALID);
            }
        }
        if (!codes.containsAll(values.keySet())) throw exception(ENTITY_VALUE_INVALID);
    }

    private static boolean finiteNumber(Object value) {
        if (!(value instanceof Number)) return false;
        try { new BigDecimal(value.toString()); return true; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static boolean date(Object value, boolean time) {
        if (!(value instanceof String text)) return false;
        try {
            if (time) LocalDateTime.parse(text); else LocalDate.parse(text);
            return true;
        } catch (java.time.format.DateTimeParseException invalid) { return false; }
    }
}
