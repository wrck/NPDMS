package cn.iocoder.yudao.module.pms.project.domain.template;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Approval definition identity lives in the version-owned binding, never in a mutable global alias. */
public final class ApprovalWorkBindingSchema {
    private ApprovalWorkBindingSchema() { }

    public record Definition(String key, String id) { }

    public static Definition read(String key, JsonNode parameters) {
        if (key == null || key.isBlank() || parameters == null || !parameters.isObject())
            throw new IllegalArgumentException("审批绑定必须配置流程 key 和精确流程定义 ID");
        var id = parameters.path("processDefinitionId");
        if (!id.isTextual() || id.asText().isBlank())
            throw new IllegalArgumentException("审批绑定缺少精确流程定义 ID，不能在运行时选择最新版本");
        var embeddedKey = parameters.get("approvalDefinitionKey");
        if (embeddedKey != null && (!embeddedKey.isTextual() || !key.equals(embeddedKey.asText())))
            throw new IllegalArgumentException("审批绑定参数中的流程 key 与节点配置不一致");
        return new Definition(key, id.asText());
    }

    /** Execution contracts use the existing parameter snapshot to retain the complete identity. */
    public static ObjectNode freeze(String key, JsonNode parameters) {
        var definition = read(key, parameters);
        ObjectNode frozen = (ObjectNode) parameters.deepCopy();
        frozen.put("approvalDefinitionKey", definition.key());
        return frozen;
    }
}
