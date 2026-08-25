package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/** 冻结模板任务定义为ProjectTask当前执行契约。 */
@Component
public class TaskExecutionContractFactory {

    public static final String TASK_NATIVE = "TASK_NATIVE";

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            TASK_NATIVE, "BUSINESS_OBJECT", "BUSINESS_COMPONENT", "DYNAMIC_FORM", "APPROVAL", "COMPOSITE");

    public ProjectTaskExecutionContractDO create(Long projectTaskId, Long templateTaskDefinitionId,
                                                  TemplateDefinitionContent.TaskDef definition,
                                                  LocalDateTime effectiveFrom) {
        validateDefinition(definition);
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setProjectTaskId(projectTaskId);
        contract.setTemplateTaskDefinitionId(templateTaskDefinitionId);
        contract.setWorkBindingTypeCode(definition.getWorkBindingTypeCode());
        contract.setTargetContextCode(definition.getTargetContextCode());
        contract.setTargetObjectType(definition.getTargetObjectType());
        contract.setTargetObjectKey(definition.getTargetObjectKey());
        contract.setComponentKey(definition.getComponentKey());
        contract.setDynamicFormRevisionId(definition.getDynamicFormRevisionId());
        contract.setBindingParameterSnapshot(definition.getBindingConfig());
        contract.setPermissionPolicyRef(definition.getPermissionPolicyRef());
        contract.setCompletionRuleTypeCode(definition.getCompletionRuleTypeCode());
        contract.setCompletionRuleSnapshot(definition.getCompletionRuleConfig());
        contract.setGateRef(definition.getGateRef());
        contract.setSourceDefinitionVersion(definition.getDefinitionVersion());
        contract.setContractVersion(1);
        contract.setEffectiveFrom(effectiveFrom);
        contract.setEffectiveTo(null);
        contract.setVersion(0);
        return contract;
    }

    public void validateDefinition(TemplateDefinitionContent.TaskDef definition) {
        if (definition == null || StringUtils.isBlank(definition.getWorkBindingTypeCode())
                || !SUPPORTED_TYPES.contains(definition.getWorkBindingTypeCode())) {
            throw new IllegalArgumentException("任务WorkBinding类型无效");
        }
        requireJson(definition.getBindingConfig(), "任务绑定配置无效");
        if (StringUtils.isBlank(definition.getPermissionPolicyRef())) {
            throw new IllegalArgumentException("任务权限策略缺失");
        }
        if (StringUtils.isBlank(definition.getCompletionRuleTypeCode())) {
            throw new IllegalArgumentException("任务完成规则类型缺失");
        }
        requireJson(definition.getCompletionRuleConfig(), "任务完成规则配置无效");
        if (definition.getDefinitionVersion() == null || definition.getDefinitionVersion() <= 0) {
            throw new IllegalArgumentException("任务定义版本无效");
        }
        validateBindingTarget(definition);
    }

    private void validateBindingTarget(TemplateDefinitionContent.TaskDef definition) {
        if (TASK_NATIVE.equals(definition.getWorkBindingTypeCode())) {
            if (StringUtils.isNotBlank(definition.getTargetContextCode())
                    || StringUtils.isNotBlank(definition.getTargetObjectType())
                    || StringUtils.isNotBlank(definition.getTargetObjectKey())
                    || StringUtils.isNotBlank(definition.getComponentKey())
                    || definition.getDynamicFormRevisionId() != null
                    || StringUtils.isNotBlank(definition.getApprovalDefinitionKey())) {
                throw new IllegalArgumentException("TASK_NATIVE不得配置外部目标");
            }
            return;
        }
        switch (definition.getWorkBindingTypeCode()) {
            case "BUSINESS_OBJECT" -> requireAll(definition.getTargetContextCode(),
                    definition.getTargetObjectType(), definition.getTargetObjectKey());
            case "BUSINESS_COMPONENT" -> requireAll(definition.getComponentKey());
            case "DYNAMIC_FORM" -> {
                if (definition.getDynamicFormRevisionId() == null) {
                    throw new IllegalArgumentException("DYNAMIC_FORM缺少发布版本");
                }
            }
            case "APPROVAL" -> requireAll(definition.getApprovalDefinitionKey());
            case "COMPOSITE" -> { /* 子视图由已校验bindingConfig承载。 */ }
            default -> throw new IllegalArgumentException("任务WorkBinding类型无效");
        }
    }

    private void requireAll(String... values) {
        for (String value : values) {
            if (StringUtils.isBlank(value)) {
                throw new IllegalArgumentException("任务绑定目标缺失");
            }
        }
    }

    private void requireJson(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        try {
            JsonUtils.parseObject(value, Object.class);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }
}
