package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NormalClosureTaskProjectionTest {
    final ProjectTaskInstanceDO task=new ProjectTaskInstanceDO().setId(21L).setCode("CLOSE").setStageCode("S6").setStatus("IN_PROGRESS");
    final ProjectTaskExecutionContractDO contract=new ProjectTaskExecutionContractDO();
    NormalClosureTaskProjectionTest(){
        task.setTenantId(1L);
        contract.setId(31L);contract.setTenantId(1L);contract.setProjectTaskId(21L);contract.setSourceNodeKey("task:close");
        contract.setWorkBindingTypeCode("PAGE");contract.setBindingParameterSnapshot("{\"routePath\":\"/pms/project/normal-closure\"}");
        contract.setCompletionRuleSnapshot("{\"predicate\":\"NORMAL_CLOSURE_APPROVED\",\"parameters\":{}}");
    }
    @Test void onlyExactApprovalTaskIsProspectivelySatisfiedWithoutAnyMutation(){
        var projection=NormalClosureTaskProjection.resolve(query());
        assertEquals(21L,projection.taskId());assertEquals("IN_PROGRESS",task.getStatus());
        assertFalse(projection.includes(new ProjectTaskInstanceDO().setId(22L).setCode("CLOSE")));
        task.setStatus("DONE");assertNull(NormalClosureTaskProjection.resolve(query()));
    }
    @Test void frozenLeafContractUsesItsTypeAndParametersWithoutRewritingHistory(){
        contract.setCompletionRuleTypeCode("NORMAL_CLOSURE_APPROVED");
        contract.setCompletionRuleSnapshot("{}");
        var projection = NormalClosureTaskProjection.resolve(query());
        assertNotNull(projection);
        assertEquals(21L, projection.taskId());
        assertEquals("{}", contract.getCompletionRuleSnapshot());
        assertEquals("IN_PROGRESS", task.getStatus());
        contract.setCompletionRuleTypeCode("TASK_NATIVE_STATUS");
        assertNull(NormalClosureTaskProjection.resolve(query()));
        contract.setCompletionRuleTypeCode("NORMAL_CLOSURE_APPROVED");
        contract.setCompletionRuleSnapshot("{\"unexpected\":true}");
        assertNull(NormalClosureTaskProjection.resolve(query()));
    }
    @Test void productionSnapshotAndContractFactoryPreserveClosureRecognition(){
        var snapshot = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot();
        var definition = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TaskContract();
        definition.setNodeKey("task:close");
        definition.setCode("CLOSE");
        definition.setStageCode("S6");
        var binding = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract();
        binding.setType("PAGE");
        binding.setParameters(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(contract.getBindingParameterSnapshot()));
        definition.setBinding(binding);
        definition.setPermission(new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.PermissionContract());
        definition.setCompletionRule(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(contract.getCompletionRuleSnapshot()));
        snapshot.getTasks().add(definition);
        var frozen = new cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory()
                .create(21L, null, snapshot.toRuntimeContent().getTasks().getFirst(), java.time.LocalDateTime.now());
        frozen.setId(31L);
        frozen.setTenantId(1L);
        assertEquals("NORMAL_CLOSURE_APPROVED", frozen.getCompletionRuleTypeCode());
        assertEquals("{}", frozen.getCompletionRuleSnapshot());
        var projection = NormalClosureTaskProjection.resolve(new NormalClosureTaskProjection.Query(
                1L, "S6", List.of(task), Map.of(21L, frozen)));
        assertNotNull(projection);
        assertEquals(21L, projection.taskId());
        assertEquals("IN_PROGRESS", task.getStatus());
    }
    @Test void notAdmittedOtherStageOrAmbiguousClosureTaskCannotBypassReadiness(){
        task.setStatus("PENDING_START");assertThrows(IllegalArgumentException.class,()->NormalClosureTaskProjection.resolve(query()));
        task.setStatus("IN_PROGRESS");task.setStageCode("S5");assertThrows(IllegalArgumentException.class,()->NormalClosureTaskProjection.resolve(query()));
        task.setStageCode("S6");
        assertThrows(IllegalArgumentException.class,()->NormalClosureTaskProjection.resolve(new NormalClosureTaskProjection.Query(1L,"S6",List.of(task,task),Map.of(21L,contract))));
        contract.setWorkBindingTypeCode("TASK_NATIVE");assertNull(NormalClosureTaskProjection.resolve(query()));
    }
    private NormalClosureTaskProjection.Query query(){return new NormalClosureTaskProjection.Query(1L,"S6",List.of(task),Map.of(21L,contract));}
}
