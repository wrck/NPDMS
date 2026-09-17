package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;

/** 统一格式分派；旧摘要由发布Owner验证，新格式只检查冻结结构，不重新编译历史。 */
public final class TemplateExecutionSnapshotReader {

    private TemplateExecutionSnapshotReader() { }

    public static TemplateExecutionSnapshot read(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        JsonNode document = JsonUtils.getObjectMapper().readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(json);
        int version = requireSupportedDocument(document);
        // 绑定原文，不能通过JSON树重序列化改变小数精度及旧Hash。
        if (version == TemplateExecutionSnapshot.SCHEMA_VERSION) return JsonUtils.parseObject(json, TemplateExecutionSnapshot.class);
        TemplateVersionSnapshot.requireDocument(document);
        TemplateExecutionSnapshot snapshot = JsonUtils.getObjectMapper().readerFor(TemplateExecutionSnapshot.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(json);
        TemplateVersionSnapshot.validate(snapshot);
        return snapshot;
    }

    public static TemplateExecutionSnapshot read(JsonNode document) {
        requireSupportedDocument(document);
        return read(JsonUtils.toJsonString(document));
    }

    private static int requireSupportedDocument(JsonNode document) {
        if (document == null || !document.isObject()) throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        JsonNode schema = document.get("executionSchemaVersion");
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt()) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_SCHEMA_UNSUPPORTED");
        }
        int version = schema.intValue();
        requireSupportedVersion(version);
        return version;
    }

    public static void requireSupportedVersion(Integer version) {
        if (!Integer.valueOf(TemplateExecutionSnapshot.SCHEMA_VERSION).equals(version)
                && !Integer.valueOf(TemplateVersionSnapshot.SCHEMA_VERSION).equals(version)) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        }
    }

    public static void validate(TemplateExecutionSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        requireSupportedVersion(snapshot.getExecutionSchemaVersion());
        if (Integer.valueOf(TemplateVersionSnapshot.SCHEMA_VERSION).equals(snapshot.getExecutionSchemaVersion())) {
            TemplateVersionSnapshot.validate(snapshot);
        }
    }
}
