package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureExitApi.ClosureExitCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.normalclosure.ClosureProjectMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectChildWaitEvents;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class NormalClosureExitExecutorTest {
    private final NormalClosureAccess access = mock(NormalClosureAccess.class);
    private final NormalClosureCheckService checks = mock(NormalClosureCheckService.class);
    private final ClosureProjectMapper closureProjects = mock(ClosureProjectMapper.class);
    private final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    private final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    private final ProjectChildWaitEvents childWaitEvents = mock(ProjectChildWaitEvents.class);
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final ProjectStageInstanceDO stage = new ProjectStageInstanceDO();
    private NormalClosureExitExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new NormalClosureExitExecutor(access, checks, closureProjects, stages, executions, childWaitEvents);
        project.setId(9L); project.setTenantId(7L); project.setVersion(3); project.setCurrentStage("S6");
        project.setActivePlanVersionId(21L); project.setLifecycleTemplateId(31L); project.setLifecycleTemplateRevisionNo(2);
        stage.setId(11L); stage.setVersion(4); stage.setProjectId(9L); stage.setTenantId(7L); stage.setStatus("ACTIVE");
        TenantContextHolder.setTenantId(7L);
        login(1L);
        when(access.lock(eq(9L), eq(3), eq(15L), any())).thenReturn(new NormalClosureAccess.Context(project, 15L));
        when(access.lockPrimaryServiceManagerUserIds(7L, 9L)).thenReturn(List.of(5L));
        when(checks.evaluateLocked(eq(project), eq(15L), eq(103L), eq(1L), eq("closure-1"))).thenReturn(evaluation());
        when(closureProjects.selectFrozenTemplateRevisionId(any())).thenReturn(51L);
        when(stages.updateStatusIfMatch(any())).thenReturn(1);
        when(closureProjects.closeProjectIfMatch(any())).thenReturn(1);
        when(closureProjects.insertExitRecord(any())).thenReturn(1);
    }

    @AfterEach
    void cleanUp() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void closesTheOnlyActiveTerminalStageRoundWithTheApprovedClosureEvidence() {
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round(31L, 21L, "ACTIVE", 1, 1)));
        when(executions.finishIfActive(any())).thenReturn(1);

        var result = executor.executeApprovedExit(command());

        assertEquals(11L, result.stageInstanceId());
        verify(checks).evaluateLocked(project, 15L, 103L, 1L, "closure-1");
        verify(executions).finishIfActive(argThat(finish -> {
            var snapshot = JsonUtils.parseTree(finish.resultSnapshot());
            return finish.tenantId().equals(7L) && finish.projectId().equals(9L)
                    && finish.executionId().equals(31L) && finish.expectedVersion().equals(1)
                    && snapshot.path("closureApplicationId").asLong() == 101L
                    && snapshot.path("closureSnapshotId").asLong() == 102L
                    && "approval-1".equals(snapshot.path("approvalProcessInstanceId").asText())
                    && "revalidation-vector".equals(snapshot.path("revalidationEvidence").asText())
                    && "digest-1".equals(snapshot.path("revalidationDigest").asText());
        }));
        verify(closureProjects).closeProjectIfMatch(any());
        verify(closureProjects).insertExitRecord(any());
    }

    @Test
    void rejectsAnOldPlanRoundBeforeChangingTheTerminalStageOrProject() {
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round(31L, 20L, "ACTIVE", 1, 1)));

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(stages, childWaitEvents);
        verify(executions, never()).finishIfActive(any());
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
    }

    @Test
    void rejectsACurrentRoundForAnotherStageBeforeChangingTheTerminalStageOrProject() {
        var wrongStage = round(31L, 21L, "ACTIVE", 1, 1);
        wrongStage.setNodeInstanceId(12L);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(wrongStage));

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(stages, childWaitEvents);
        verify(executions, never()).finishIfActive(any());
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
    }

    @Test
    void rejectsANonActiveTerminalStageRoundBeforeChangingTheTerminalStageOrProject() {
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round(31L, 21L, "DONE", 1, 1)));

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(stages, childWaitEvents);
        verify(executions, never()).finishIfActive(any());
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
    }

    @Test
    void preservesCompletedTerminalStageAndRoundHistoryDuringConfiguredClosure() {
        stage.setStatus("DONE");
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round(31L, 21L, "DONE", 1, 2)));

        executor.executeApprovedExit(command());

        verifyNoInteractions(stages);
        verify(executions, never()).finishIfActive(any());
        verify(closureProjects).closeProjectIfMatch(any());
        verify(closureProjects).insertExitRecord(any());
    }

    @Test
    void rejectsCompletedStageWithActiveRoundWithoutChangingClosureState() {
        stage.setStatus("DONE");
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round(31L, 21L, "ACTIVE", 1, 2)));

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(stages);
        verify(executions, never()).finishIfActive(any());
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
    }

    @Test
    void rejectsRoundVersionConflictAndTheMethodIsDeclaredToRollBackTheExistingTransaction() throws Exception {
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round(31L, 21L, "ACTIVE", 1, 1)));
        when(executions.finishIfActive(any())).thenReturn(0);

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verify(stages).updateStatusIfMatch(any());
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
        Method method = NormalClosureExitExecutor.class.getMethod("executeApprovedExit", ClosureExitCommand.class);
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertNotNull(transaction);
        assertEquals(Propagation.MANDATORY, transaction.propagation());
        assertArrayEquals(new Class<?>[] { Exception.class }, transaction.rollbackFor());
    }

    @Test
    void keepsTheLegacyNoExecutionPlanClosurePath() {
        project.setActivePlanVersionId(null);

        executor.executeApprovedExit(command());

        verifyNoInteractions(executions);
        verify(closureProjects).closeProjectIfMatch(any());
        verify(closureProjects).insertExitRecord(any());
    }

    @Test
    void rejectsADifferentLoggedInReviewerBeforeProjectOrOwnerWrites() {
        login(2L);

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(access, checks, stages, executions, childWaitEvents);
        verifyNoInteractions(closureProjects);
    }

    @Test
    void rejectsAMissingOrDifferentTenantReviewerContextBeforeProjectOrOwnerWrites() {
        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));
        verifyNoInteractions(access, checks, stages, executions, childWaitEvents, closureProjects);

        login(1L);
        TenantContextHolder.setTenantId(8L);
        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));
        verifyNoInteractions(access, checks, stages, executions, childWaitEvents, closureProjects);
    }

    @Test
    void propagatesOwnerRevalidationFailureWithoutChangingTheStageOrProject() {
        when(checks.evaluateLocked(eq(project), eq(15L), eq(103L), eq(1L), eq("closure-1")))
                .thenThrow(new IllegalStateException("OWNER_FACT_FORBIDDEN"));

        assertThrows(IllegalStateException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(stages, executions, childWaitEvents);
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
    }

    @Test
    void rejectsAChangedOwnerFactDigestBeforeChangingTheStageOrProject() {
        when(checks.evaluateLocked(eq(project), eq(15L), eq(103L), eq(1L), eq("closure-1")))
                .thenReturn(evaluation("changed-digest"));

        assertThrows(RuntimeException.class, () -> executor.executeApprovedExit(command()));

        verifyNoInteractions(stages, executions, childWaitEvents);
        verify(closureProjects, never()).closeProjectIfMatch(any());
        verify(closureProjects, never()).insertExitRecord(any());
    }

    private NormalClosureCheckService.Evaluation evaluation() {
        return evaluation("digest-1");
    }

    private NormalClosureCheckService.Evaluation evaluation(String digest) {
        return new NormalClosureCheckService.Evaluation("{\"reviewerUserId\":1}",
                List.of(new NormalClosureCheckService.Check("CLOSURE", true, null, 9L)), "evidence",
                "revalidation-vector", digest, new ProjectRuntimeGraphResolver.Resolution(stage, null, null, null, List.of(), List.of()));
    }

    private ClosureExitCommand command() {
        return new ClosureExitCommand(7L, 9L, 3, 15L, 103L, "closure-1", 101L, 102L, 1L,
                "S6", 5L, "digest-1", "approval-1", 4);
    }

    private void login(Long userId) {
        var user = new LoginUser();
        user.setId(userId); user.setTenantId(7L); user.setUserType(2);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
    }

    private ProjectNodeExecutionDO round(Long id, Long planVersionId, String status, Integer currentMarker, Integer version) {
        var round = new ProjectNodeExecutionDO();
        round.setId(id); round.setTenantId(7L); round.setProjectId(9L); round.setPlanVersionId(planVersionId);
        round.setNodeKind("STAGE"); round.setNodeInstanceId(11L); round.setStatus(status);
        round.setCurrentMarker(currentMarker); round.setVersion(version);
        return round;
    }
}
