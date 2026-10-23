package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist.CutoverChecklistItemDO;
import tools.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.Set;

/** Serial-number answers reference the same fixed task device scope as command collection. */
final class CutoverChecklistDeviceValidator {
    private CutoverChecklistDeviceValidator() { }

    record Query(CutoverChecklistItemDO item, String answer, Set<String> serialNumbers) { }

    static boolean referencesTaskDevices(CutoverChecklistItemDO item) {
        return item != null && referencesSerial(JsonUtils.parseTree(item.getInterfaceSchemaSnapshot()));
    }

    private static boolean referencesSerial(JsonNode schema) {
        if (schema == null || schema.isNull()) return false;
        if ("serialNumber".equals(schema.path("code").asText())) return true;
        for (String key : new String[]{"fields", "columns"}) {
            for (JsonNode child : schema.path(key)) if (referencesSerial(child)) return true;
        }
        return false;
    }

    static void validate(Query query) {
        if (!referencesTaskDevices(query.item())) return;
        JsonNode value = JsonUtils.parseTree(query.answer());
        value = value.has("value") ? value.get("value") : value;
        if ("TABLE".equals(query.item().getInterfaceFormatCode()) && value.isTextual()) value = JsonUtils.parseTree(value.asText());
        validateValue(JsonUtils.parseTree(query.item().getInterfaceSchemaSnapshot()), value, query);
    }

    private static void validateValue(JsonNode schema, JsonNode value, Query query) {
        if (value == null || value.isNull()) return;
        if ("serialNumber".equals(schema.path("code").asText())
                && (!value.isTextual() || !value.asText().isBlank() && !query.serialNumbers().contains(value.asText()))) {
            throw new CutoverChecklistException(CutoverChecklistException.Code.INVALID_REQUEST,
                    query.item().getItemName() + "：设备序列号须从当前割接设备清单中选择");
        }
        if (schema.path("columns").isArray()) {
            JsonNode rows = value.isArray() ? value : value.path("rows");
            for (JsonNode row : rows) validateFields(schema.path("columns"), row, query);
        } else if (schema.path("fields").isArray()) validateFields(schema.path("fields"), value, query);
    }

    private static void validateFields(JsonNode fields, JsonNode row, Query query) {
        for (JsonNode field : fields) {
            JsonNode condition = field.path("visibleWhen");
            if (condition.isObject() && !Objects.equals(row.get(condition.path("field").asText()), condition.get("equals"))) continue;
            validateValue(field, row.get(field.path("code").asText()), query);
        }
    }
}
