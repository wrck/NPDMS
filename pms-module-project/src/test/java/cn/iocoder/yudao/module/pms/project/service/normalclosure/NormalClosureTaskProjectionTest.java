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
    @Test void notAdmittedOtherStageOrAmbiguousClosureTaskCannotBypassReadiness(){
        task.setStatus("PENDING_START");assertThrows(IllegalArgumentException.class,()->NormalClosureTaskProjection.resolve(query()));
        task.setStatus("IN_PROGRESS");task.setStageCode("S5");assertThrows(IllegalArgumentException.class,()->NormalClosureTaskProjection.resolve(query()));
        task.setStageCode("S6");
        assertThrows(IllegalArgumentException.class,()->NormalClosureTaskProjection.resolve(new NormalClosureTaskProjection.Query(1L,"S6",List.of(task,task),Map.of(21L,contract))));
        contract.setWorkBindingTypeCode("TASK_NATIVE");assertNull(NormalClosureTaskProjection.resolve(query()));
    }
    private NormalClosureTaskProjection.Query query(){return new NormalClosureTaskProjection.Query(1L,"S6",List.of(task),Map.of(21L,contract));}
}
