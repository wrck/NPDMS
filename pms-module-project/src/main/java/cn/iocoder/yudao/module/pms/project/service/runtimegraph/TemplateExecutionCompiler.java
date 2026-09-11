package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * PM-03 V2 compiler boundary.
 *
 * Designer content is editable input. Runtime must consume executionSnapshot only.
 * This class intentionally does not resolve DefinitionRevision rows.
 */
@Service
public class TemplateExecutionCompiler {

    private static final int EXECUTION_SCHEMA_VERSION = 2;

    public JsonNode compile(TemplateDefinitionContent designerDocument) {
        if (designerDocument == null) {
            throw new IllegalArgumentException("designer document required");
        }
        JsonNode snapshot = designerDocument.getExecutionSnapshot();
        if (snapshot != null && snapshot.path("executionSchemaVersion").asInt() >= EXECUTION_SCHEMA_VERSION) {
            return snapshot;
        }

        throw new IllegalArgumentException(
                "V2 template requires compiled execution snapshot; legacy definition resolution is not a compiler input");
    }

    public String compileJson(TemplateDefinitionContent designerDocument) {
        return JsonUtils.toJsonString(compile(designerDocument));
    }
}
