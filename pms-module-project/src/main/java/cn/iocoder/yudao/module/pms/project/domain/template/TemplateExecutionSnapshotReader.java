package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

/**
 * Reads an explicitly versioned snapshot without recompiling or resolving live definitions.
 * Publication metadata and legacy hash verification remain the publication owner's responsibility.
 */
public final class TemplateExecutionSnapshotReader {

    private TemplateExecutionSnapshotReader() { }

    public static TemplateExecutionSnapshot read(String json) {
        requireSupportedDocument(JsonUtils.parseObject(json, JsonNode.class));
        // Bind the original text: a tree round-trip can normalize decimals used by the legacy hash.
        return JsonUtils.parseObject(json, TemplateExecutionSnapshot.class);
    }

    public static TemplateExecutionSnapshot read(JsonNode document) {
        requireSupportedDocument(document);
        return JsonUtils.parseObject(JsonUtils.toJsonString(document), TemplateExecutionSnapshot.class);
    }

    private static void requireSupportedDocument(JsonNode document) {
        // Check the stored token before data binding can supply defaults or coerce strings/numbers.
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        }
        JsonNode schema = document.get("executionSchemaVersion");
        if (schema == null || !schema.isIntegralNumber()
                || !Integer.toString(TemplateExecutionSnapshot.SCHEMA_VERSION).equals(schema.asText())) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_SCHEMA_UNSUPPORTED");
        }
    }

    public static void requireSupportedVersion(Integer version) {
        if (!Integer.valueOf(TemplateExecutionSnapshot.SCHEMA_VERSION).equals(version)) {
            throw new IllegalArgumentException("EXECUTION_SNAPSHOT_V2_REQUIRED");
        }
    }
}
