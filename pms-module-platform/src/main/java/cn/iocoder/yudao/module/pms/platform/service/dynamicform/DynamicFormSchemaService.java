package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_FIELD_KEY_DUPLICATE;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.DYNAMIC_FORM_SCHEMA_INVALID;

/** Validates the frozen FormCreate structure without rewriting its payload. */
@Service
public class DynamicFormSchemaService {

    public static final String ENGINE_CODE = "FORM_CREATE_ELEMENT_PLUS";
    public static final String DESIGNER_VERSION = "3.4.0";
    public static final String RENDERER_VERSION = "3.2.38";
    public static final String FILE_COMPONENT_TYPE = "PmsFileArtifact";
    public static final String FILE_PURPOSE_PREFIX = "FORM_FIELD_ATTACHMENT/";
    public static final int MAX_FILE_FIELD_KEY_LENGTH = 42;

    public SchemaFields parseAndValidate(String formConfJson, String formRulesJson,
                                         String engineCode, String designerVersion, String rendererVersion) {
        try {
            return validateAndDescribe(JsonUtils.parseTree(formConfJson), JsonUtils.parseTree(formRulesJson),
                    engineCode, designerVersion, rendererVersion);
        } catch (RuntimeException invalid) {
            if (invalid instanceof cn.iocoder.yudao.framework.common.exception.ServiceException) {
                throw invalid;
            }
            throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        }
    }

    public SchemaFields validateAndDescribe(JsonNode formConf, JsonNode formRules,
                                            String engineCode, String designerVersion, String rendererVersion) {
        if (formConf == null || !formConf.isObject() || formRules == null || !formRules.isArray()
                || !ENGINE_CODE.equals(engineCode) || !DESIGNER_VERSION.equals(designerVersion)
                || !RENDERER_VERSION.equals(rendererVersion)) {
            throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        }
        LinkedHashSet<String> allFields = new LinkedHashSet<>();
        List<String> ordinaryFields = new ArrayList<>();
        List<String> fileFields = new ArrayList<>();
        List<DynamicFormFieldDescriptor> descriptors = new ArrayList<>();
        for (JsonNode rule : formRules) {
            walkRule(rule, allFields, ordinaryFields, fileFields, descriptors);
        }
        return new SchemaFields(ordinaryFields, fileFields, descriptors);
    }

    private void walkRule(JsonNode rule, Set<String> allFields,
                          List<String> ordinaryFields, List<String> fileFields,
                          List<DynamicFormFieldDescriptor> descriptors) {
        if (rule == null || !rule.isObject()) {
            throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        }
        JsonNode fieldNode = rule.get("field");
        if (fieldNode != null && !fieldNode.isNull()) {
            if (!fieldNode.isTextual()) {
                throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
            }
            String field = fieldNode.textValue();
            if (field.isBlank()) {
                if (FILE_COMPONENT_TYPE.equals(rule.path("type").asText())) {
                    throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
                }
            } else if (!field.equals(field.trim())) {
                throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
            } else if (!allFields.add(field)) {
                throw exception(DYNAMIC_FORM_FIELD_KEY_DUPLICATE);
            } else if (FILE_COMPONENT_TYPE.equals(rule.path("type").asText())) {
                validateFileField(field);
                fileFields.add(field);
                descriptors.add(descriptor(rule, field, true));
            } else {
                ordinaryFields.add(field);
                descriptors.add(descriptor(rule, field, false));
            }
        }
        JsonNode children = rule.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                walkRule(child, allFields, ordinaryFields, fileFields, descriptors);
            }
        }
    }

    private DynamicFormFieldDescriptor descriptor(JsonNode rule, String field, boolean controlledFile) {
        JsonNode validations = rule.path("validate");
        boolean required = false;
        Integer minLength = null;
        Integer maxLength = null;
        String pattern = null;
        if (validations.isArray()) {
            for (JsonNode validation : validations) {
                required |= validation.path("required").asBoolean(false);
                if (validation.has("min") && validation.get("min").canConvertToInt()) {
                    minLength = validation.get("min").intValue();
                }
                if (validation.has("max") && validation.get("max").canConvertToInt()) {
                    maxLength = validation.get("max").intValue();
                }
                if (validation.path("pattern").isTextual()) {
                    pattern = validation.path("pattern").textValue();
                    Pattern.compile(pattern);
                }
            }
        }
        String type = rule.path("type").asText("unknown");
        String valueType = switch (type) {
            case "switch" -> "boolean";
            case "inputNumber", "slider", "rate" -> "number";
            case "checkbox" -> "array";
            default -> controlledFile ? "controlled-file" : "any";
        };
        List<String> allowed = new ArrayList<>();
        JsonNode options = rule.path("options");
        if (!options.isArray()) options = rule.path("props").path("options");
        if (options.isArray()) {
            for (JsonNode option : options) {
                JsonNode value = option.get("value");
                if (value != null && value.isValueNode()) allowed.add(value.asText());
            }
        }
        return new DynamicFormFieldDescriptor(field, type, controlledFile, required, valueType,
                minLength, maxLength, pattern, allowed);
    }

    private void validateFileField(String field) {
        if (field.contains("/") || field.length() > MAX_FILE_FIELD_KEY_LENGTH
                || (FILE_PURPOSE_PREFIX.length() + field.length()) > 64) {
            throw exception(DYNAMIC_FORM_SCHEMA_INVALID);
        }
    }

    public record SchemaFields(List<String> ordinaryFieldKeys, List<String> fileFieldKeys,
                               List<DynamicFormFieldDescriptor> descriptors) {
        public SchemaFields(List<String> ordinaryFieldKeys, List<String> fileFieldKeys) {
            this(ordinaryFieldKeys, fileFieldKeys, java.util.stream.Stream.concat(
                    ordinaryFieldKeys.stream().map(key -> new DynamicFormFieldDescriptor(
                            key, "unknown", false, false, "any", null, null, null, List.of())),
                    fileFieldKeys.stream().map(key -> new DynamicFormFieldDescriptor(
                            key, FILE_COMPONENT_TYPE, true, false, "controlled-file", null, null, null, List.of())))
                    .toList());
        }

        public SchemaFields {
            ordinaryFieldKeys = List.copyOf(ordinaryFieldKeys);
            fileFieldKeys = List.copyOf(fileFieldKeys);
            descriptors = List.copyOf(descriptors);
        }

        public boolean isFileField(String fieldKey) {
            return fileFieldKeys.contains(fieldKey);
        }
    }
}
