package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import tools.jackson.databind.JsonNode;
import java.util.Objects;

/** 无主办理绑定的订阅任务只生成运行兼容标识；实际订阅规则仍由版本快照唯一持有。 */
public final class ResultSubscriptionTaskContract {
    public static final String TYPE = "RESULT_SUBSCRIPTION";
    private static final String PARAMETERS = "{\"executionMode\":\"RESULT_SUBSCRIPTION\"}";
    private static final String PERMISSION = "{\"policySnapshot\":{\"requiredActions\":[\"QUERY\"]}}";
    private ResultSubscriptionTaskContract() { }

    public static boolean pure(JsonNode execution) {
        if (execution == null) return false;
        var config=TemplateExecutionConfiguration.read(execution);
        return !config.subscriptions().isEmpty() && config.operations().isEmpty() && config.presentation() == null;
    }

    public static void project(TemplateExecutionSnapshot.TaskContract source, Integer schema, TemplateDefinitionContent.TaskDef target) {
        if (source.getBinding() != null) return;
        require(Integer.valueOf(TemplateVersionSnapshot.SCHEMA_VERSION).equals(schema) && pure(source.getExecution()) && source.getPermission() == null);
        target.setWorkBindingTypeCode(TYPE);
        // 编译投影携带配置用于工厂检查，但物理合同只保存固定标识，避免建立第二份订阅真值。
        target.setBindingConfig(JsonUtils.toJsonString(source.getExecution()));
        target.setPermissionPolicyRef(null);target.setPermissionSnapshot(JsonUtils.parseTree(PERMISSION));
        target.setDefinitionVersion(TemplateVersionSnapshot.SCHEMA_VERSION);
    }

    public static void requireDefinition(TemplateDefinitionContent.TaskDef definition) {
        require(TYPE.equals(definition.getWorkBindingTypeCode()) && pure(JsonUtils.parseTree(definition.getBindingConfig()))
                && Integer.valueOf(TemplateVersionSnapshot.SCHEMA_VERSION).equals(definition.getDefinitionVersion())
                && text(definition.getSourceNodeKey()) && definition.getPermissionPolicyRef() == null
                && JsonUtils.parseTree(PERMISSION).equals(definition.getPermissionSnapshot())
                && definition.getTargetContextCode() == null && definition.getTargetObjectType() == null && definition.getTargetObjectKey() == null
                && definition.getComponentKey() == null && definition.getDynamicFormRevisionId() == null
                && definition.getApprovalDefinitionKey() == null && definition.getBindingViewSnapshot() == null);
    }

    public static void requireRuntime(ProjectTaskExecutionContractDO contract) {
        require(contract != null && TYPE.equals(contract.getWorkBindingTypeCode())
                && Integer.valueOf(TemplateVersionSnapshot.SCHEMA_VERSION).equals(contract.getSourceDefinitionVersion())
                && text(contract.getSourceNodeKey()) && contract.getPermissionPolicyRef() == null
                && JsonUtils.parseTree(PERMISSION).equals(JsonUtils.parseTree(contract.getPermissionSnapshot()))
                && JsonUtils.parseTree(PARAMETERS).equals(JsonUtils.parseTree(contract.getBindingParameterSnapshot()))
                && contract.getTargetContextCode() == null && contract.getTargetObjectType() == null && contract.getTargetObjectKey() == null
                && contract.getComponentKey() == null && contract.getDynamicFormRevisionId() == null
                && contract.getApprovalInstanceId() == null && contract.getBindingViewSnapshot() == null);
    }

    public static boolean matches(ProjectTaskExecutionContractDO contract, TemplateExecutionSnapshot.TaskContract node) {
        requireRuntime(contract);
        return node.getBinding() == null && node.getPermission() == null && pure(node.getExecution())
                && Objects.equals(contract.getSourceNodeKey(),node.getNodeKey());
    }
    public static String parameters() { return PARAMETERS; }
    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("RESULT_SUBSCRIPTION_TASK_CONTRACT_INVALID");
    }
}
