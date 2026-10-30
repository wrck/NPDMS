package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskPrerequisiteApiImplTest {
    private final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    private final ProjectStageAdmissionService admission = mock(ProjectStageAdmissionService.class);
    private final ProjectTaskPrerequisiteApiImpl api = new ProjectTaskPrerequisiteApiImpl(projects, plans, graph, contracts,
            new org.springframework.beans.factory.ObjectProvider<>() {
                @Override public ProjectStageAdmissionService getObject() { return admission; }
            });
    private final ProjectTaskPrerequisiteApi.Query query = new ProjectTaskPrerequisiteApi.Query(9L, "SOL", "IMPLEMENTATION_SOLUTION");
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final ProjectPlanVersionDO plan = new ProjectPlanVersionDO();
    private final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
    private final ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
    private final ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        project.setId(9L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(51L);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        plan.setId(51L);
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(TemplateExecutionSnapshot.freezeJson(snapshot)); return plan; });
        var definition = new TemplateExecutionSnapshot.TaskContract();
        definition.setNodeKey("task:solution"); definition.setCode("SOLUTION"); definition.setStageCode("DESIGN"); definition.setRequired(true);
        var binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setTargetContextCode("SOL"); binding.setTargetObjectType("IMPLEMENTATION_SOLUTION"); definition.setBinding(binding);
        snapshot.setTasks(List.of(definition));
        task.setId(2114152121376350214L); task.setProjectId(9L); task.setCode("SOLUTION"); task.setStageCode("DESIGN"); task.setStatus("PENDING_START");
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(task));
        contract.setTenantId(1L); contract.setProjectTaskId(task.getId()); contract.setSourceNodeKey("task:solution");
        contract.setTargetContextCode("SOL"); contract.setTargetObjectType("IMPLEMENTATION_SOLUTION");
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(admission.submissionPrerequisites(any())).thenReturn(new ProjectStageAdmissionService.TaskAdmissionView(RuleEvaluation.Outcome.MATCHED, List.of()));
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void unmetAndUnknownFrozenPreconditionsBlockSubmissionBeforeAnyMutation() {
        for (var outcome : List.of(RuleEvaluation.Outcome.NOT_MATCHED, RuleEvaluation.Outcome.UNKNOWN)) {
            when(admission.submissionPrerequisites(any())).thenReturn(new ProjectStageAdmissionService.TaskAdmissionView(outcome,
                    List.of(new ProjectStageAdmissionService.TaskAdmissionCondition("现场勘察", outcome))));
            assertFalse(api.inspect(query).allowed());
            assertThrows(IllegalArgumentException.class, () -> api.requireSatisfied(query));
        }
        assertEquals("PENDING_START", task.getStatus());
        verify(projects, never()).updateById(any(ProjectTaskInstanceDO.class));
    }
    @Test void satisfiedCurrentBindingCanSubmitAndCompletedTaskIsNotReopened() {
        task.setStatus("DONE");
        assertTrue(api.inspect(query).allowed()); api.requireSatisfied(query);
        assertEquals("DONE", task.getStatus());
        verify(admission, times(2)).submissionPrerequisites(new ProjectStageAdmissionService.TaskPrerequisiteQuery(project, task, contract));
        var order = inOrder(projects, plans, graph, contracts);
        order.verify(projects).selectProjectForCommandForUpdate(any());
        order.verify(plans).selectEffective(any()); order.verify(graph).selectTasksForUpdate(any());
        order.verify(contracts).selectCurrentByTaskIdForUpdate(any());
    }
    @Test void foreignTenantClosedProjectAndStalePlanNeverGrantReadiness() {
        project.setTenantId(2L); assertFalse(api.inspect(query).allowed());
        project.setTenantId(1L); project.setLifecycleStatus("CLOSED"); assertFalse(api.inspect(query).allowed());
        project.setLifecycleStatus("ACTIVE"); plan.setId(52L); assertFalse(api.inspect(query).allowed());
        verifyNoInteractions(admission);
    }
    @Test void ambiguousOrRetiredBindingFailsClosed() {
        snapshot.setTasks(List.of(snapshot.getTasks().getFirst(), snapshot.getTasks().getFirst()));
        assertFalse(api.inspect(query).allowed());
        snapshot.setTasks(List.of(snapshot.getTasks().getFirst()));
        contract.setEffectiveTo(java.time.LocalDateTime.now()); assertFalse(api.inspect(query).allowed());
        contract.setEffectiveTo(null); contract.setSourceNodeKey("old-plan-task"); assertFalse(api.inspect(query).allowed());
        verifyNoInteractions(admission);
    }
    @Test void unmanagedIndependentBusinessRemainsAvailableButMissingManagedPlanDoesNot() {
        project.setActivePlanVersionId(null); assertTrue(api.inspect(query).allowed());
        project.setLifecycleTemplateId(13L); assertFalse(api.inspect(query).allowed());
        verifyNoInteractions(plans, graph, contracts, admission);
    }
    @Test void completedUnrelatedOptionalTasksAreNotInventedAsPreconditions() {
        snapshot.setTasks(List.of()); assertTrue(api.inspect(query).allowed());
        verifyNoInteractions(graph, contracts, admission);
    }
}
