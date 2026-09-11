package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskBusinessBindingTest {
    private ProjectTaskExecutionContractDO contract() {
        var c = new ProjectTaskExecutionContractDO(); c.setWorkBindingRevisionId(22L);
        c.setWorkBindingTypeCode("BUSINESS_OBJECT"); c.setTargetContextCode("SOL"); c.setTargetObjectType("REQUIREMENT_ANALYSIS");
        c.setComponentKey("PROJ_REQUIREMENT_ANALYSIS");
        c.setBindingParameterSnapshot("{\"schemaVersion\":1,\"dynamicFormTemplateId\":7,\"dynamicFormTemplateRevisionId\":8,\"dynamicFormRevisionNo\":1,\"dynamicFormRevisionFactVersion\":2}");
        c.setDefinitionSnapshot("[{\"definition\":{\"id\":22,\"definitionKind\":\"WORK_BINDING\",\"schemaVersion\":1,\"payload\":{\"bindingType\":\"BUSINESS_OBJECT\",\"targetContextCode\":\"SOL\",\"targetObjectType\":\"REQUIREMENT_ANALYSIS\",\"businessViewRevisionId\":\"88\",\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}}}]");
        return c;
    }
    @Test void specializedFormSnapshotAndExactFrozenViewHaveSeparateSources() {
        var c = contract(); String parameters = c.getBindingParameterSnapshot();
        var binding = TaskBusinessBinding.parse(c);
        assertEquals(88L, binding.businessViewRevisionId()); assertEquals("REFERENCE_EXISTING", binding.instanceResolutionStrategy());
        assertEquals(parameters, c.getBindingParameterSnapshot()); assertNull(binding.unavailableReason());
    }
    @Test void explicitViewConflictCannotOverrideFrozenDefinition() {
        var c = contract(); c.setBindingParameterSnapshot("{\"businessViewRevisionId\":99}");
        assertThrows(RuntimeException.class, () -> TaskBusinessBinding.parse(c));
    }
    @Test void missingClosureIsNotAReasonToReadLatestOrGuessAView() {
        var c = contract(); c.setDefinitionSnapshot(null);
        assertThrows(RuntimeException.class, () -> TaskBusinessBinding.parse(c));
    }
}
