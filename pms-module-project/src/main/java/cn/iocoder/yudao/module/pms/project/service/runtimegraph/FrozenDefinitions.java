package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/** PM-03: only the publication closure already frozen with the project; never resolves latest revisions. */
public final class FrozenDefinitions {
    private final Map<Long, JsonNode> definitions = new LinkedHashMap<>();

    public FrozenDefinitions(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isArray()) throw new IllegalArgumentException("DEFINITION_SNAPSHOT_NOT_FROZEN");
        for (JsonNode entry : snapshot) {
            JsonNode definition = entry.path("definition");
            long id = definition.path("id").asLong();
            if (id <= 0 || definitions.putIfAbsent(id, definition) != null)
                throw new IllegalArgumentException("INVALID_FROZEN_DEFINITION_ID");
        }
    }

    public FrozenDefinitions(String snapshot) { this(JsonUtils.parseObject(snapshot, JsonNode.class)); }

    public JsonNode require(Long id, String kind) {
        JsonNode definition = definitions.get(id);
        if (definition == null || !kind.equals(definition.path("definitionKind").asText())
                || definition.path("schemaVersion").asInt() != 1)
            throw new IllegalArgumentException("FROZEN_DEFINITION_MISSING: " + kind + ":" + id);
        return definition.path("payload");
    }
}
