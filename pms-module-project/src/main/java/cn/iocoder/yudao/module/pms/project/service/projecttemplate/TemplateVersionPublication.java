package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateVersionSnapshot;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;

import java.util.Objects;

/** 格式3发布行的只读边界；身份来自原发布行，不增加摘要或业务版本字段。 */
final class TemplateVersionPublication {
    private TemplateVersionPublication() { }

    record Frozen(TemplateDesignerDocument designer, TemplateExecutionSnapshot snapshot) { }

    static boolean applies(ProjectTemplateRevisionDO row) {
        return row != null && Integer.valueOf(TemplateVersionSnapshot.SCHEMA_VERSION).equals(row.getExecutionSchemaVersion());
    }

    static Frozen read(ProjectTemplateRevisionDO row, Long tenantId, Long templateId, Integer revisionNo) {
        require(applies(row) && positive(row.getId()) && tenantId != null && positive(templateId)
                && revisionNo != null && revisionNo > 0
                && Objects.equals(tenantId, row.getTenantId()) && Objects.equals(templateId, row.getTemplateId())
                && Objects.equals(revisionNo, row.getRevisionNo())
                && TemplateRules.REVISION_STATUS_PUBLISHED.equals(row.getStatus()), "发布版本身份不一致");
        require(row.getSnapshotHash() == null && text(row.getCompilerVersion()) && text(row.getExecutionSnapshot()),
                "格式3发布元数据不完整或混入旧Hash");
        TemplateExecutionSnapshot snapshot = TemplateExecutionSnapshotReader.read(row.getExecutionSnapshot());
        require(Objects.equals(row.getExecutionSchemaVersion(), snapshot.getExecutionSchemaVersion())
                && Objects.equals(row.getCompilerVersion(), snapshot.getCompilerVersion()), "发布格式/编译器元数据不一致");
        return new Frozen(designer(row), snapshot);
    }

    private static TemplateDesignerDocument designer(ProjectTemplateRevisionDO row) {
        require(text(row.getDesignerDocument())
                && Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(row.getDesignerSchemaVersion()),
                "发布版本缺少冻结Designer");
        JsonNode document = JsonUtils.getObjectMapper().readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(row.getDesignerDocument());
        JsonNode schema = document == null || !document.isObject() ? null : document.get("schemaVersion");
        require(schema != null && schema.isIntegralNumber() && schema.canConvertToInt()
                && schema.intValue() == TemplateDesignerDocument.SCHEMA_VERSION, "冻结Designer格式无效");
        // 同样绑定原文，保留业务数值和版本内完整设计，不从Snapshot反编译Designer。
        return JsonUtils.getObjectMapper().readerFor(TemplateDesignerDocument.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT).readValue(row.getDesignerDocument());
    }

    private static boolean positive(Long id) { return id != null && id > 0; }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalArgumentException("VERSION_PUBLICATION_INVALID: " + reason);
    }
}
