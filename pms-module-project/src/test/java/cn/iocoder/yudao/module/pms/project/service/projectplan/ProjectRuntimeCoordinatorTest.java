package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRuntimeCoordinatorTest {
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper projects =
            mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class);
    private final cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project =
            new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO();

    @org.junit.jupiter.api.BeforeEach void activeProject() {
        project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectById(9L)).thenReturn(project);
    }
    @Test void admissionProgressReevaluatesEarlierDependentStageWithoutWaitingForAnUnrelatedEvent() {
        TenantContextHolder.setTenantId(7L);
        try {
            var admission = mock(ProjectStageAdmissionService.class);
            var completion = mock(ProjectStageCompletionService.class);
            var closure = mock(ProjectRuleClosureService.class);
            var tasks = mock(ProjectBusinessTaskCompletionService.class);
            var gates = mock(ProjectGateRuleService.class);
            var graph = mock(ProjectRuntimeGraphMapper.class);
            var associations = mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService.class);
            var dependent = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO().setId(11L).setStatus("PENDING");
            var source = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO().setId(12L).setStatus("PENDING");
            when(graph.selectStages(any())).thenReturn(List.of(dependent, source));
            when(admission.activateStage(9L, 1L, "test", 11L)).thenAnswer(call -> {
                boolean matched = "ACTIVE".equals(source.getStatus());
                if (matched) dependent.setStatus("ACTIVE");
                return List.of(new ProjectStageAdmissionService.StageAdmission(11L, "dependent",
                        matched ? RuleEvaluation.Outcome.MATCHED : RuleEvaluation.Outcome.NOT_MATCHED, null, matched));
            });
            when(admission.activateStage(9L, 1L, "test", 12L)).thenAnswer(call -> {
                source.setStatus("ACTIVE");
                return List.of(new ProjectStageAdmissionService.StageAdmission(12L, "source", RuleEvaluation.Outcome.MATCHED, null, true));
            });
            when(tasks.completeEligible(9L, "test")).thenReturn(new ProjectBusinessTaskCompletionService.Result(0, 0, false));
            when(completion.completeStage(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(new ProjectStageCompletionService.Completion(0, false));
            when(closure.closeIfSatisfied(9L, 1L, "test")).thenReturn(new ProjectRuleClosureService.Closure(false, false));
            var result = new ProjectRuntimeCoordinator(admission, completion, closure, tasks, gates, graph, associations, projects).reevaluate(9L, 1L, "test");
            assertEquals(2, result.activated()); assertFalse(result.unknown()); assertEquals(0, result.completed());
            verify(admission, never()).activateEligible(anyLong(), anyLong(), anyString());
            verify(admission, times(2)).activateStage(9L, 1L, "test", 11L);
            verify(admission).activateStage(9L, 1L, "test", 12L);
            verify(tasks, times(3)).completeEligible(9L, "test");
        } finally { TenantContextHolder.clear(); }
    }

    @Test void failedStageOwnerRollsBackBeforeIndependentStageCompletionAndUsesOnlyTheExistingRetryLoop() {
        TenantContextHolder.setTenantId(7L);
        try {
            var admission = mock(ProjectStageAdmissionService.class);
            var completion = mock(ProjectStageCompletionService.class);
            var closure = mock(ProjectRuleClosureService.class);
            var tasks = mock(ProjectBusinessTaskCompletionService.class);
            var gates = mock(ProjectGateRuleService.class);
            var graph = mock(ProjectRuntimeGraphMapper.class);
            var associations = mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService.class);
            var coordinator = new ProjectRuntimeCoordinator(admission,completion,closure,tasks,gates,graph,associations,projects);
            var bad = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO().setId(11L).setStatus("ACTIVE");
            var good = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO().setId(12L).setStatus("ACTIVE");
            when(graph.selectStages(any())).thenReturn(List.of(bad,good));
            doThrow(new IllegalStateException("Owner transaction rolled back")).when(associations).synchronizeStage(9L,11L,"test");
            when(tasks.completeEligible(9L,"test")).thenReturn(new ProjectBusinessTaskCompletionService.Result(0,0,false));
            when(completion.completeStage(9L,12L,1L,"test")).thenReturn(new ProjectStageCompletionService.Completion(1,false),new ProjectStageCompletionService.Completion(0,false));
            when(closure.closeIfSatisfied(9L,1L,"test")).thenReturn(new ProjectRuleClosureService.Closure(false,false));
            var result = coordinator.reevaluate(9L,1L,"test");
            assertEquals(1,result.completed()); assertTrue(result.unknown());
            verify(completion,never()).completeStage(9L,11L,1L,"test");
            verify(associations,times(2)).synchronizeStage(9L,12L,"test");
            verify(completion,times(2)).completeStage(9L,12L,1L,"test");
        } finally { TenantContextHolder.clear(); }
    }
    @Test void failedGateDoesNotStopIndependentGatesOrTasksAndTaskProgressTriggersAnotherPass() {
        TenantContextHolder.setTenantId(7L);
        try {
            var admission = mock(ProjectStageAdmissionService.class);
            var completion = mock(ProjectStageCompletionService.class);
            var closure = mock(ProjectRuleClosureService.class);
            var tasks = mock(ProjectBusinessTaskCompletionService.class);
            var gates = mock(ProjectGateRuleService.class);
            var graph = mock(ProjectRuntimeGraphMapper.class);
            var associations = mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService.class);
            var coordinator = new ProjectRuntimeCoordinator(admission,completion,closure,tasks,gates,graph,associations,projects);
            var bad = new ProjectGateInstanceDO(); bad.setGateCode("BAD");
            var good = new ProjectGateInstanceDO(); good.setGateCode("GOOD");
            when(graph.selectGates(any())).thenReturn(List.of(bad,good));
            when(gates.evaluate(9L,"BAD",1L,"test")).thenThrow(new IllegalStateException("gate transaction rolled back"));
            var matched = new RuleEvaluation("plan:51:gate:21",RuleEvaluation.Outcome.MATCHED,null,List.of(),List.of(),List.of());
            when(gates.evaluate(9L,"GOOD",1L,"test")).thenReturn(new ProjectGateRuleService.Result(matched,"GOOD:PASSED:1"));
            when(tasks.completeEligible(9L,"test")).thenReturn(new ProjectBusinessTaskCompletionService.Result(0,1,false),new ProjectBusinessTaskCompletionService.Result(1,0,false),new ProjectBusinessTaskCompletionService.Result(0,0,false));
            when(closure.closeIfSatisfied(9L,1L,"test")).thenReturn(new ProjectRuleClosureService.Closure(false,false));
            var result = coordinator.reevaluate(9L,1L,"test");
            assertTrue(result.unknown()); assertEquals(1,result.completed());
            assertEquals(1,result.activated());
            var order = inOrder(gates,tasks);
            for (int i=0;i<3;i++) {
                order.verify(gates).evaluate(9L,"BAD",1L,"test");
                order.verify(gates).evaluate(9L,"GOOD",1L,"test");
                order.verify(tasks).completeEligible(9L,"test");
            }
            verify(closure).closeIfSatisfied(9L,1L,"test");
        } finally { TenantContextHolder.clear(); }
    }
}
