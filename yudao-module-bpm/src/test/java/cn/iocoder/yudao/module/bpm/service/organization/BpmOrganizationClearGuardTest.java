package cn.iocoder.yudao.module.bpm.service.organization;

import cn.iocoder.yudao.module.system.api.organization.OrganizationClearGuard;
import cn.iocoder.yudao.module.bpm.dal.mysql.definition.BpmProcessDefinitionInfoMapper;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.bpmn.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BpmOrganizationClearGuardTest {
    @Test void nestedDepartmentCandidateIsCheckedWithinTenant() {
        var repository=mock(RepositoryService.class);
        var definitions=mock(BpmProcessDefinitionInfoMapper.class);
        var query=mock(ProcessDefinitionQuery.class);
        var definition=mock(ProcessDefinition.class);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionTenantId("1")).thenReturn(query);
        when(query.listPage(0,100)).thenReturn(List.of(definition));
        when(definition.getId()).thenReturn("flow1");when(definition.getName()).thenReturn("测试流程");
        var model=new BpmnModel();var process=new org.flowable.bpmn.model.Process();model.addProcess(process);
        var nested=new SubProcess();process.addFlowElement(nested);var task=new UserTask();nested.addFlowElement(task);
        BpmnModelUtils.addCandidateElements(20,"20",task);
        when(repository.getBpmnModel("flow1")).thenReturn(model);
        var guard=new BpmOrganizationClearGuard(repository,definitions);
        assertThrows(IllegalArgumentException.class,()->guard.check(new OrganizationClearGuard.Scope(1L,Set.of(),Set.of(20L))));
        assertDoesNotThrow(()->guard.check(new OrganizationClearGuard.Scope(1L,Set.of(),Set.of(21L))));
        verify(query,times(2)).processDefinitionTenantId("1");
    }
}
