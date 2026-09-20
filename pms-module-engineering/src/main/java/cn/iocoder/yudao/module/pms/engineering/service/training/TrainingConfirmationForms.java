package cn.iocoder.yudao.module.pms.engineering.service.training;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.TRAINING_ARGUMENT_INVALID;

/** Public pages receive declarative fields only; admin scripts and remote controls never cross this boundary. */
final class TrainingConfirmationForms {
    static final long DEFAULT_TEMPLATE = 992209200001L;
    private static final Set<String> TYPES = Set.of("input", "radio", "checkbox", "select", "inputNumber", "signaturePad");
    static String defaults() {
        try (var input = new ClassPathResource("training/customer-confirmation.json").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException("Default confirmation form missing", failure); }
    }
    static String safeSnapshot(String json) {
        JsonNode rules = JsonUtils.parseTree(json);
        if (!rules.isArray() || rules.size() > 40) throw invalid("确认模板必须包含不超过 40 个平铺字段");
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        for (JsonNode rule : rules) {
            String field = rule.path("field").asText(), type = rule.path("type").asText();
            if (!field.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}") || !fields.add(field) || !TYPES.contains(type)
                    || rule.has("children") || (type.equals("signaturePad") && !field.equals("signatureImageDataUrl")))
                throw invalid("确认模板包含不支持的字段或控件");
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("field", field); clean.put("type", type); clean.put("title", rule.path("title").asText(field));
            List<Map<String, Object>> options = new ArrayList<>();
            if (rule.path("options").isArray()) for (JsonNode option : rule.get("options")) {
                if (!option.path("value").isTextual()) throw invalid("确认模板选项必须使用文本值");
                options.add(Map.of("label", option.path("label").asText(), "value", option.get("value").asText()));
            }
            if (!options.isEmpty()) clean.put("options", options);
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("placeholder", rule.path("props").path("placeholder").asText(""));
            if (type.equals("input")) {
                props.put("type", "textarea".equals(rule.path("props").path("type").asText()) ? "textarea" : "text");
                props.put("rows", 3); props.put("maxlength", Math.max(1, Math.min(2000, rule.path("props").path("maxlength").asInt(500))));
            }
            if (!type.equals("signaturePad")) clean.put("props", props);
            boolean required = false;
            for (JsonNode check : rule.path("validate")) required |= check.path("required").asBoolean();
            clean.put("validate", List.of(Map.of("required", required, "message", "请填写" + clean.get("title"))));
            result.add(clean);
        }
        // Required business fields and rating codes cannot be removed or redefined by presentation configuration.
        for (JsonNode core : JsonUtils.parseTree(defaults())) {
            String field = core.get("field").asText();
            if (field.equals("signOpinion")) continue;
            Map<String, Object> actual = result.stream().filter(r -> field.equals(r.get("field"))).findFirst().orElseThrow(() -> invalid("确认模板缺少必填业务字段：" + field));
            JsonNode value = JsonUtils.parseTree(JsonUtils.toJsonString(actual));
            if (!core.get("type").asText().equals(actual.get("type")) || !value.path("validate").get(0).path("required").asBoolean()) throw invalid("确认模板必填业务字段不允许改变控件类型或取消必填");
            if (core.has("options")) {
                Set<String> expected = new HashSet<>(), configured = new HashSet<>();
                core.get("options").forEach(o -> expected.add(o.get("value").asText()));
                value.path("options").forEach(o -> configured.add(o.get("value").asText()));
                if (!expected.equals(configured)) throw invalid("确认模板评价选项值不允许改变");
            }
        }
        return JsonUtils.toJsonString(result);
    }
    static String validateValues(String rulesJson, Map<String, Object> values) {
        if (JsonUtils.toJsonString(values).length() > 32768) throw invalid("确认内容过长");
        Map<String, Object> accepted = new LinkedHashMap<>();
        for (JsonNode rule : JsonUtils.parseTree(rulesJson)) {
            String field = rule.get("field").asText();
            if (field.equals("signatureImageDataUrl")) continue;
            Object value = values.get(field);
            boolean empty = value == null || value instanceof String s && s.isBlank() || value instanceof Collection<?> list && list.isEmpty();
            if (rule.path("validate").get(0).path("required").asBoolean() && empty) throw invalid("请填写" + rule.path("title").asText());
            if (empty) continue;
            String type = rule.path("type").asText();
            if (type.equals("input") && (!(value instanceof String text) || text.length() > rule.path("props").path("maxlength").asInt(500))) throw invalid("文本长度或类型不正确");
            if (type.equals("inputNumber") && !(value instanceof Number)) throw invalid("请填写数字");
            if (Set.of("radio", "select", "checkbox").contains(type)) {
                Set<String> options = new HashSet<>(); rule.path("options").forEach(o -> options.add(o.get("value").asText()));
                if (type.equals("checkbox")) {
                    if (!(value instanceof List<?> list) || !list.stream().allMatch(options::contains)) throw invalid("选择项不正确");
                } else if (!options.contains(value)) throw invalid("选择项不正确");
            }
            accepted.put(field, value);
        }
        return JsonUtils.toJsonString(accepted);
    }
    private static RuntimeException invalid(String message) { return exception(TRAINING_ARGUMENT_INVALID, message); }
}
