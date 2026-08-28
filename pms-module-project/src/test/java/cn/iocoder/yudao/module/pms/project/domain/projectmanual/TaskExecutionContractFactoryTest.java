package cn.iocoder.yudao.module.pms.project.domain.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskExecutionContractFactoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 10, 0);

    private final TaskExecutionContractFactory factory = new TaskExecutionContractFactory();

    @Test
    void taskNativeRejectsExternalTarget() {
        TemplateDefinitionContent.TaskDef task = validTaskNative();
        task.setTargetObjectKey("foreign-1");

        assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, task, NOW));
    }

    @Test
    void executableTaskRequiresPermissionPolicyAndCompletionRule() {
        TemplateDefinitionContent.TaskDef task = validTaskNative();
        task.setPermissionPolicyRef(null);

        assertThrows(IllegalArgumentException.class, () -> factory.create(11L, 21L, task, NOW));
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
        task.setCompletionRuleConfig("{\"schemaVersion\":1,\"requiredStatus\":\"COMPLETED\"}");
        task.setDefinitionVersion(1);
        return task;
    }
}
