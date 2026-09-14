package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskExecutionContractFactoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    private final TaskExecutionContractFactory factory = new TaskExecutionContractFactory();

    @Test void approvalContractPreservesBothDefinitionKeyAndExactIdWithoutMutatingItsSource() {
        var task = validTaskNative(); task.setWorkBindingTypeCode("APPROVAL"); task.setApprovalDefinitionKey("approval");
        task.setBindingConfig("{\"processDefinitionId\":\"approval:1:101\",\"formCode\":\"review\"}");
        var contract = factory.create(11L, 21L, task, NOW);
        var frozen = JsonUtils.parseTree(contract.getBindingParameterSnapshot());
        assertEquals("approval", frozen.path("approvalDefinitionKey").asText());
        assertEquals("approval:1:101", frozen.path("processDefinitionId").asText());
        assertEquals("review", frozen.path("formCode").asText());
        assertNull(contract.getApprovalInstanceId());
        assertEquals("{\"processDefinitionId\":\"approval:1:101\",\"formCode\":\"review\"}", task.getBindingConfig());
    }

    @Test void approvalContractRejectsFloatingOrConflictingDefinitionIdentity() {
        var task = validTaskNative(); task.setWorkBindingTypeCode("APPROVAL"); task.setApprovalDefinitionKey("approval");
        for (String parameters : java.util.List.of("{}", "{\"processDefinitionId\":123}", "{\"processDefinitionId\":\" \"}",
                "{\"processDefinitionId\":\"approval:1:101\",\"approvalDefinitionKey\":\"other\"}")) {
            task.setBindingConfig(parameters);
            assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, task, NOW));
        }
    }

    @Test
    void nativeWorkMayUseACompiledConditionGroupInsteadOfTheOldFixedCompletionShape() {
        var definition = validTaskNative();
        definition.setCompletionRuleTypeCode("ALL");
        definition.setCompletionRuleConfig("{\"operator\":\"ALL\",\"rules\":[{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}},{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}]}");
        assertEquals("ALL",factory.create(11L,21L,definition,NOW).getCompletionRuleTypeCode());
    }

    @Test
    void taskNativeRejectsExternalTarget() {
        TemplateDefinitionContent.TaskDef task = validTaskNative();
        task.setTargetObjectKey("foreign-1");
        assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, task, NOW));
    }

    @Test
    void taskNativeRejectsArbitraryCompletionRuleShape() {
        TemplateDefinitionContent.TaskDef task = validTaskNative();
        task.setCompletionRuleTypeCode("ALL");
        assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, task, NOW));

        TemplateDefinitionContent.TaskDef finalTask = validTaskNative();
        finalTask.setCompletionRuleConfig("{\"requiredStatus\":\"DONE\",\"fallback\":true}");
        assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, finalTask, NOW));
    }

    @Test
    void legacyExecutableTaskStillRequiresPermissionPolicy() {
        TemplateDefinitionContent.TaskDef task = validTaskNative();
        task.setPermissionPolicyRef(null);
        assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, task, NOW));
    }

    @Test
    void v2ExecutableTaskMayUseFrozenPermissionSnapshotWithoutLegacyPolicyRef() {
        TemplateDefinitionContent.TaskDef task = validTaskNative();
        task.setPermissionPolicyRef(null);
        task.setSourceNodeKey("task:T-001");
        task.setPermissionSnapshot(JsonUtils.parseObject("{\"requiredActions\":[\"VIEW\"]}", JsonNode.class));

        ProjectTaskExecutionContractDO contract = factory.create(11L, null, task, NOW);
        assertEquals("task:T-001", contract.getSourceNodeKey());
        assertNull(contract.getPermissionPolicyRef());
        assertNotNull(contract.getPermissionSnapshot());
    }

    @Test
    void createsCurrentVersionOneContract() {
        ProjectTaskExecutionContractDO contract = factory.create(11L, 21L, validTaskNative(), NOW);
        assertEquals(11L, contract.getProjectTaskId());
        assertEquals(21L, contract.getTemplateTaskDefinitionId());
        assertEquals("TASK_NATIVE", contract.getWorkBindingTypeCode());
        assertEquals(1, contract.getSourceDefinitionVersion());
        assertEquals(1, contract.getContractVersion());
        assertEquals(NOW, contract.getEffectiveFrom());
        assertNull(contract.getEffectiveTo());
    }

    @Test
    void createsManualTaskNativeContractWithCanonicalDoneRule() {
        ProjectTaskExecutionContractDO contract = factory.createTaskNative(12L, NOW);
        assertEquals("TASK_NATIVE", contract.getWorkBindingTypeCode());
        assertEquals("PROJECT_TASK_NATIVE_DEFAULT", contract.getPermissionPolicyRef());
        assertEquals("TASK_NATIVE_STATUS", contract.getCompletionRuleTypeCode());
        assertEquals("{\"requiredStatus\":\"DONE\"}", contract.getCompletionRuleSnapshot());
        assertNull(contract.getTargetObjectKey());
    }

    private TemplateDefinitionContent.TaskDef validTaskNative() {
        TemplateDefinitionContent.TaskDef task = new TemplateDefinitionContent.TaskDef();
        task.setTaskCode("T-001");
        task.setStageCode("S0");
        task.setWorkBindingTypeCode("TASK_NATIVE");
        task.setBindingConfig("{\"schemaVersion\":1}");
        task.setPermissionPolicyRef("PROJECT_TASK_NATIVE_DEFAULT");
        task.setCompletionRuleTypeCode("TASK_NATIVE_STATUS");
        task.setCompletionRuleConfig("{\"requiredStatus\":\"DONE\"}");
        task.setDefinitionVersion(1);
        return task;
    }
}
