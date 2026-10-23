package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

/** 与冻结的采集项 schema 一起校验，暂存允许缺项，提交才检查必填。 */
public final class CutoverChecklistAnswerValidator {
    private CutoverChecklistAnswerValidator() { }

    public static void validate(CutoverChecklistItemDO item, String answer, boolean submitting) {
        try {
            JsonNode data = JsonUtils.parseTree(answer);
            if (data == null || !data.isObject()) invalid(item.getItemName(), "答案格式无效");
            data = data.has("value") ? data.get("value") : data;
            JsonNode schema = JsonUtils.parseTree(item.getInterfaceSchemaSnapshot());
            if (schema == null || !schema.isObject()) invalid(item.getItemName(), "冻结字段定义无效");
            if ("TABLE".equals(item.getInterfaceFormatCode()) && data.isTextual()) {
                data = JsonUtils.parseTree(data.asText());
            }
            if ("TABLE".equals(item.getInterfaceFormatCode()) && data != null && !data.isNull()
                    && !data.isObject() && !data.isArray()) invalid(item.getItemName(), "须填写结构化表格");
            boolean required = submitting && Boolean.TRUE.equals(item.getRequiredFlag());
            validateValue(schema, data, required, submitting, item.getItemName());
        } catch (CutoverChecklistException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            invalid(item.getItemName(), "答案须符合冻结的字段格式");
        }
    }

    private static void validateValue(JsonNode schema, JsonNode value, boolean required, boolean submitting, String label) {
        if (empty(value)) {
            if (required) invalid(label, "尚未填写");
            return;
        }
        JsonNode columns = schema.path("columns");
        JsonNode fields = schema.path("fields");
        if (columns.isArray() && !columns.isEmpty()) {
            JsonNode rows = value.isArray() ? value : value.path("rows");
            if (!rows.isArray()) invalid(label, "须填写表格行");
            if (required && rows.isEmpty()) invalid(label, "至少填写一行");
            for (JsonNode row : rows) {
                if (!row.isObject()) invalid(label, "表格行格式无效");
                validateFields(columns, row, submitting, label);
            }
        } else if (fields.isArray() && !fields.isEmpty()) {
            if (!value.isObject()) invalid(label, "须填写结构化字段");
            validateFields(fields, value, submitting, label);
        } else if (schema.path("options").isArray() && !schema.path("options").isEmpty()) {
            boolean matched = false;
            for (JsonNode option : schema.get("options")) {
                JsonNode expected = option.isObject() ? option.get("value") : option;
                if (Objects.equals(value, expected)) matched = true;
            }
            if (!matched) invalid(label, "选项无效");
        } else {
            String type = schema.path("type").asText("").toUpperCase(java.util.Locale.ROOT);
            if ("BOOLEAN".equals(type) && !value.isBoolean()) invalid(label, "须选择是或否");
            if ("NUMBER".equals(type) && !value.isNumber()) invalid(label, "须填写数字");
        }
    }

    private static void validateFields(JsonNode fields, JsonNode values, boolean submitting, String label) {
        for (JsonNode field : fields) {
            String code = field.path("code").asText(field.path("key").asText(""));
            if (code.isBlank()) invalid(label, "冻结字段编码缺失");
            JsonNode condition = field.path("visibleWhen");
            if (condition.isObject() && !Objects.equals(values.get(condition.path("field").asText()), condition.get("equals"))) continue;
            String text = field.path("label").asText(field.path("name").asText(code));
            validateValue(field, values.get(code), submitting && field.path("required").asBoolean(false), submitting, label + " / " + text);
        }
    }

    private static boolean empty(JsonNode value) {
        return value == null || value.isNull() || value.isTextual() && value.asText().isBlank()
                || (value.isObject() || value.isArray()) && value.isEmpty();
    }

    private static void invalid(String label, String message) {
        throw new CutoverChecklistException(CutoverChecklistException.Code.INVALID_REQUEST, label + "：" + message);
    }
}
