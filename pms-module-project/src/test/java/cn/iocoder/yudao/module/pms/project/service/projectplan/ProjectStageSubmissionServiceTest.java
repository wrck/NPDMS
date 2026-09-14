package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageSubmissionServiceTest {
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    final ProjectRuntimeCoordinator coordinator = mock(ProjectRuntimeCoordinator.class);
    final ProjectMasterMapper projectRows = mock(ProjectMasterMapper.class);
    final ProjectStageSubmissionService service = new ProjectStageSubmissionService(projects, plans, executions, scopes, permissions, commands, coordinator, projectRows);
    ProjectNodeExecutionDO round;
    ProjectMasterDO project;
    ProjectPlanVersionDO plan;
    TemplateExecutionSnapshot snapshot;
    PlatformCommandExecutionApi.SuccessFacts recorded;
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(7L);
        when(permissions.hasAnyPermissions(1L,"pms:project-task:execute")).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
        project = new ProjectMasterDO(); project.setId(9L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project); when(projectRows.selectById(9L)).thenReturn(project);
        plan = new ProjectPlanVersionDO(); plan.setId(51L); when(plans.selectEffective(any())).thenReturn(plan);
        snapshot = new TemplateExecutionSnapshot(); var node = new TemplateExecutionSnapshot.StageContract(); node.setNodeKey("stage:one");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setType("STAGE_NATIVE"); node.setBinding(binding);
        snapshot.setStages(List.of(node)); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        round = new ProjectNodeExecutionDO(); round.setId(31L); round.setPlanVersionId(51L); round.setNodeKey("stage:one");
        round.setNodeKind("STAGE"); round.setStatus("ACTIVE"); round.setVersion(1); round.setRoundNo(1);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round)); when(executions.selectCurrent(any())).thenReturn(List.of(round));
        when(executions.selectById(31L)).thenReturn(round);
        when(executions.submitIfCurrent(any())).thenReturn(1);
        when(commands.execute(any(), anyString(), eq(ProjectStageSubmissionService.Submitted.class), any(), any())).thenAnswer(call -> {
            var result = ((Supplier<ProjectStageSubmissionService.Submitted>)call.getArgument(3)).get();
            recorded = ((Function<ProjectStageSubmissionService.Submitted,PlatformCommandExecutionApi.SuccessFacts>)call.getArgument(4)).apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,result);
        });
    }
    ProjectStageSubmissionService.Command command(int version) { return new ProjectStageSubmissionService.Command(9L,31L,version,"本轮工作已完成"); }
    @Test void writesEvidenceForThisRoundAndEmitsOnlySafeMetadata() {
        service.submit(command(1),1L,"intent");
        verify(executions).submitIfCurrent(argThat(q -> q.executionId().equals(31L) && q.expectedVersion()==1 && q.note().equals("本轮工作已完成")));
        assertFalse(recorded.detailSnapshot().contains("本轮工作已完成"));
        var event = recorded.businessEvents().getFirst(); assertEquals(event.eventId(),JsonUtils.parseTree(event.eventPayload()).path("eventId").asText());
        verify(coordinator).reevaluate(eq(9L),eq(1L),anyString());
    }
    @Test void cannotUseAnOldRoundOrOverwriteSubmittedEvidence() {
        assertThrows(RuntimeException.class,() -> service.submit(command(0),1L,"intent"));
        round.setSubmittedAt(java.time.LocalDateTime.now());
        assertThrows(RuntimeException.class,() -> service.submit(command(1),1L,"intent2"));
        verify(executions,never()).submitIfCurrent(any());
    }
    @Test void cannotManuallySubstituteAnOwnerBusinessResult() {
        snapshot.getStages().getFirst().getBinding().setType("BUSINESS_OBJECT"); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertThrows(RuntimeException.class,() -> service.submit(command(1),1L,"intent"));
        verify(executions,never()).submitIfCurrent(any());
    }
    @Test void permissionOrScopeDenialPrecedesExecutionReads() {
        when(permissions.hasAnyPermissions(1L,"pms:project-task:execute")).thenReturn(false);
        assertThrows(RuntimeException.class,() -> service.submit(command(1),1L,"intent"));
        verifyNoInteractions(projects,executions,commands);
    }
    @Test void closedProjectsDoNotOfferManualSubmission() {
        project.setLifecycleStatus("EXCEPTION_CLOSED");
        assertFalse(service.list(9L,1L).getFirst().canSubmit());
        assertThrows(RuntimeException.class,() -> service.submit(command(1),1L,"intent"));
        verify(executions,never()).submitIfCurrent(any());
    }
}
