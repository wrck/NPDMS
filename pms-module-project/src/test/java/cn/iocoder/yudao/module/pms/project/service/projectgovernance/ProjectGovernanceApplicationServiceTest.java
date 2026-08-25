package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGovernanceStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.ExceptionCloseProjectCommand;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.GovernanceActionResult;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.ReopenProjectCommand;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.RollbackProjectCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_ACTION_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_STATE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_GOVERNANCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService.PERMISSION_CLOSE;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService.PERMISSION_REOPEN;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService.PERMISSION_ROLLBACK;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService.EXCEPTION_CLOSE_SCOPE;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService.REOPEN_SCOPE;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService.GovernanceAction.EXCEPTION_CLOSE;
import static cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService.GovernanceAction.ROLLBACK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectGovernanceApplicationServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 11L;
    private static final Long ROOT_ID = 10L;
    private static final Long ACTOR_ID = 9L;
    private static final Integer VERSION = 5;

    private PlatformCommandExecutionApi commandExecutionApi;
    private PermissionApi permissionApi;
    private ProjectTreeScopeService treeScopeService;
    private ProjectGovernanceGuardService guardService;
    private ProjectMasterMapper projectMapper;
    private ProjectMemberAssignmentMapper memberMapper;
    private ProjectTreeVersionMapper treeVersionMapper;
    private ProjectStageSnapshotMapper snapshotMapper;
    private ProjectStageSnapshotRepository snapshotRepository;
    private ProjectGovernanceApplicationService service;
    private AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        commandExecutionApi = mock(PlatformCommandExecutionApi.class);
        permissionApi = mock(PermissionApi.class);
        treeScopeService = mock(ProjectTreeScopeService.class);
        guardService = mock(ProjectGovernanceGuardService.class);
        projectMapper = mock(ProjectMasterMapper.class);
        memberMapper = mock(ProjectMemberAssignmentMapper.class);
        treeVersionMapper = mock(ProjectTreeVersionMapper.class);
        snapshotMapper = mock(ProjectStageSnapshotMapper.class);
        snapshotRepository = mock(ProjectStageSnapshotRepository.class);
        service = new ProjectGovernanceApplicationService(commandExecutionApi, permissionApi,
                treeScopeService, guardService, projectMapper, memberMapper, treeVersionMapper,
                snapshotMapper, snapshotRepository);
        successFacts = new AtomicReference<>();
        stubNewExecution();
        when(permissionApi.hasAnyPermissions(ACTOR_ID, PERMISSION_ROLLBACK)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(ACTOR_ID, PERMISSION_CLOSE)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(ACTOR_ID, PERMISSION_REOPEN)).thenReturn(true);
        ProjectMasterDO project = project();
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(project);
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(tree());
        when(memberMapper.selectCurrentServiceManagerAssignments(any())).thenReturn(List.of(primary()));
        when(guardService.verifyAndRevalidate("guard", PROJECT_ID, ROLLBACK, VERSION, actor()))
                .thenReturn(verifiedGuard());
        when(guardService.verifyAndRevalidate("close-guard", PROJECT_ID,
                EXCEPTION_CLOSE, VERSION, actor())).thenReturn(verifiedGuard(EXCEPTION_CLOSE));
        when(projectMapper.updateGovernanceStateIfMatch(any())).thenReturn(1);
        when(memberMapper.closeEffectiveServiceManagerAssignments(any())).thenReturn(2);
        when(snapshotMapper.selectNextSnapshotNo(any())).thenReturn(4);
        when(snapshotRepository.append(any())).thenAnswer(invocation -> {
            ProjectStageSnapshotDO snapshot = invocation.getArgument(0);
            snapshot.setId(91L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRollbackCurrentPrimaryProjectAndPublishFacts() {
        GovernanceActionResult result = service.rollback(command("a".repeat(64)), actor());

        assertEquals("ACTIVE", result.lifecycleStatus());
        assertEquals("S0", result.currentStage());
        assertEquals("UNASSIGNED", result.assignmentStatus());
        assertEquals(6, result.projectVersion());
        assertEquals(91L, result.stageSnapshotId());
        assertFalse(result.replayed());
        ArgumentCaptor<ProjectScopeQuery> scope = ArgumentCaptor.forClass(ProjectScopeQuery.class);
        verify(treeScopeService).assertFullAccess(scope.capture());
        assertEquals("PROJECT_MANAGE", scope.getValue().actionCode());
        assertEquals(7L, scope.getValue().expectedTreeVersion());

        ArgumentCaptor<ProjectGovernanceStateUpdate> update =
                ArgumentCaptor.forClass(ProjectGovernanceStateUpdate.class);
        verify(projectMapper).updateGovernanceStateIfMatch(update.capture());
        assertEquals("ACTIVE", update.getValue().lifecycleStatus());
        assertEquals("S0", update.getValue().currentStage());
        assertEquals("UNASSIGNED", update.getValue().assignmentStatus());
        ArgumentCaptor<ProjectStageSnapshotDO> snapshot = ArgumentCaptor.forClass(ProjectStageSnapshotDO.class);
        verify(snapshotRepository).append(snapshot.capture());
        assertEquals("S3", snapshot.getValue().getBeforeStage());
        assertEquals("ROLLBACK", snapshot.getValue().getOperationType());
        assertEquals("需要重新匹配属地服务经理", snapshot.getValue().getReassignmentRequirement());
        assertNotNull(snapshot.getValue().getGuardSnapshotJson());
        assertEquals(7L, snapshot.getValue().getTreeVersion());
        assertEquals("ProjectStageChanged", successFacts.get().eventType());
        assertTrue(successFacts.get().eventPayload().contains("\"beforeState\""));
        assertTrue(successFacts.get().detailSnapshot().contains("\"guardResultSummary\""));
        assertTrue(successFacts.get().detailSnapshot().contains("\"idempotencyKey\":\"idem-1\""));
    }

    @Test
    void shouldRollbackLegacyPrimaryWithNullAssignmentType() {
        ProjectMemberAssignmentDO legacyPrimary = primary();
        legacyPrimary.setAssignmentType(null);
        when(memberMapper.selectCurrentServiceManagerAssignments(any())).thenReturn(List.of(legacyPrimary));

        GovernanceActionResult result = service.rollback(command("0".repeat(64)), actor());

        assertEquals("ROLLBACK", result.action());
        assertEquals("S0", result.currentStage());
        verify(projectMapper).updateGovernanceStateIfMatch(any());
        verify(snapshotRepository).append(any());
    }

    @Test
    void shouldExceptionCloseAndPublishFrozenCloseFacts() {
        GovernanceActionResult result = service.close(closeCommand("1".repeat(64)), actor());

        assertEquals("EXCEPTION_CLOSED", result.lifecycleStatus());
        assertEquals("S3", result.currentStage());
        assertEquals("UNASSIGNED", result.assignmentStatus());
        verify(memberMapper).closeEffectiveServiceManagerAssignments(any());
        ArgumentCaptor<ProjectStageSnapshotDO> snapshot = ArgumentCaptor.forClass(ProjectStageSnapshotDO.class);
        verify(snapshotRepository).append(snapshot.capture());
        assertEquals("EXCEPTION_CLOSE", snapshot.getValue().getOperationType());
        assertEquals("S3", snapshot.getValue().getBeforeStage());
        assertEquals("S3", snapshot.getValue().getAfterStage());
        assertEquals("客户书面确认终止实施", snapshot.getValue().getBusinessBasis());
        assertTrue(snapshot.getValue().getLegacyItemsJson().contains("遗留设备移交"));
        assertEquals("ProjectClosed", successFacts.get().eventType());
        assertTrue(successFacts.get().eventPayload().contains("\"lifecycleStatus\":\"EXCEPTION_CLOSED\""));
        assertTrue(successFacts.get().detailSnapshot().contains("\"guardResultSummary\""));
        ArgumentCaptor<PlatformCommandExecutionApi.IdempotencyScope> scope =
                ArgumentCaptor.forClass(PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), anyString(),
                eq(GovernanceActionResult.class), any(), any());
        assertEquals(EXCEPTION_CLOSE_SCOPE, scope.getValue().scopeCode());
    }

    @Test
    void shouldRejectChangedCloseGuardWithoutBusinessWrites() {
        when(guardService.verifyAndRevalidate("close-guard", PROJECT_ID,
                EXCEPTION_CLOSE, VERSION, actor()))
                .thenThrow(exception(PROJECT_GOVERNANCE_VERSION_CONFLICT));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.close(closeCommand("2".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), error.getCode());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
        verify(memberMapper, never()).closeEffectiveServiceManagerAssignments(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldRejectCloseWithoutStablePermission() {
        when(permissionApi.hasAnyPermissions(ACTOR_ID, PERMISSION_CLOSE)).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.close(closeCommand("6".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_ACTION_FORBIDDEN.getCode(), error.getCode());
        verify(projectMapper, never()).selectByIdForUpdate(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldReopenLatestExceptionCloseWithoutRestoringMemberIntervals() {
        ProjectMasterDO closed = project();
        closed.setLifecycleStatus("EXCEPTION_CLOSED");
        closed.setCurrentStage("S4");
        closed.setAssignmentStatus("UNASSIGNED");
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(closed);
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(closed);
        when(snapshotRepository.selectLatestReusableExceptionCloseForUpdate(any()))
                .thenReturn(exceptionCloseSnapshot(92L, "S2"));

        GovernanceActionResult result = service.reopen(reopenCommand(92L, "3".repeat(64)), actor());

        assertEquals("REOPEN", result.action());
        assertEquals("ACTIVE", result.lifecycleStatus());
        assertEquals("S2", result.currentStage());
        ArgumentCaptor<ProjectGovernanceStateUpdate> update =
                ArgumentCaptor.forClass(ProjectGovernanceStateUpdate.class);
        verify(projectMapper).updateGovernanceStateIfMatch(update.capture());
        assertEquals("EXCEPTION_CLOSED", update.getValue().expectedLifecycleStatus());
        assertEquals("S2", update.getValue().currentStage());
        verify(memberMapper, never()).closeEffectiveServiceManagerAssignments(any());
        ArgumentCaptor<ProjectStageSnapshotDO> snapshot = ArgumentCaptor.forClass(ProjectStageSnapshotDO.class);
        verify(snapshotRepository).append(snapshot.capture());
        assertEquals("REOPEN", snapshot.getValue().getOperationType());
        assertEquals(92L, snapshot.getValue().getRelatedSnapshotId());
        assertEquals("ProjectStageChanged", successFacts.get().eventType());
        assertTrue(successFacts.get().eventPayload().contains("\"action\":\"REOPEN\""));
        ArgumentCaptor<PlatformCommandExecutionApi.IdempotencyScope> scope =
                ArgumentCaptor.forClass(PlatformCommandExecutionApi.IdempotencyScope.class);
        verify(commandExecutionApi).execute(scope.capture(), anyString(),
                eq(GovernanceActionResult.class), any(), any());
        assertEquals(REOPEN_SCOPE, scope.getValue().scopeCode());
    }

    @Test
    void shouldRejectNormalClosedProjectWithoutReadingCloseSnapshot() {
        ProjectMasterDO normalClosed = project();
        normalClosed.setLifecycleStatus("NORMAL_CLOSED");
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(normalClosed);
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(normalClosed);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reopen(reopenCommand(92L, "4".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_STATE_INVALID.getCode(), error.getCode());
        verify(snapshotRepository, never()).selectLatestReusableExceptionCloseForUpdate(any());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
    }

    @Test
    void shouldRejectNonLatestOrConsumedExceptionCloseSnapshot() {
        ProjectMasterDO closed = project();
        closed.setLifecycleStatus("EXCEPTION_CLOSED");
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(closed);
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(closed);
        when(snapshotRepository.selectLatestReusableExceptionCloseForUpdate(any()))
                .thenReturn(exceptionCloseSnapshot(93L, "S2"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reopen(reopenCommand(92L, "5".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_STATE_INVALID.getCode(), error.getCode());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldRejectReopenWithoutStablePermission() {
        when(permissionApi.hasAnyPermissions(ACTOR_ID, PERMISSION_REOPEN)).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.reopen(reopenCommand(92L, "7".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_ACTION_FORBIDDEN.getCode(), error.getCode());
        verify(projectMapper, never()).selectByIdForUpdate(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldRejectNonPrimaryWithoutSuccessfulSideEffects() {
        when(memberMapper.selectCurrentServiceManagerAssignments(any())).thenReturn(List.of());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.rollback(command("b".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_ACTION_FORBIDDEN.getCode(), error.getCode());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldRejectChangedGuardBeforeAnyBusinessWrite() {
        when(guardService.verifyAndRevalidate("guard", PROJECT_ID, ROLLBACK, VERSION, actor()))
                .thenThrow(exception(PROJECT_GOVERNANCE_VERSION_CONFLICT));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.rollback(command("c".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), error.getCode());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
        verify(memberMapper, never()).closeEffectiveServiceManagerAssignments(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldRejectProjectCasConflictBeforeIntervalsAndSnapshot() {
        when(projectMapper.updateGovernanceStateIfMatch(any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.rollback(command("e".repeat(64)), actor()));

        assertEquals(PROJECT_GOVERNANCE_VERSION_CONFLICT.getCode(), error.getCode());
        verify(memberMapper, never()).closeEffectiveServiceManagerAssignments(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldRejectSameKeyWithDifferentDigestWithoutBusinessWrites() {
        when(commandExecutionApi.execute(any(), anyString(), eq(GovernanceActionResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.rollback(command("f".repeat(64)), actor()));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), error.getCode());
        verify(projectMapper, never()).selectByIdForUpdate(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void shouldReplayOriginalResultWithoutDuplicatingBusinessWrites() {
        GovernanceActionResult original = new GovernanceActionResult(PROJECT_ID, "ROLLBACK",
                "ACTIVE", "S3", "ASSIGNED", "ACTIVE", "S0", "UNASSIGNED",
                6, 91L, "operation-1", LocalDateTime.now(), false);
        when(commandExecutionApi.execute(any(), anyString(), eq(GovernanceActionResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, original));

        GovernanceActionResult replay = service.rollback(command("d".repeat(64)), actor());

        assertTrue(replay.replayed());
        assertEquals("operation-1", replay.operationId());
        verify(projectMapper, never()).selectByIdForUpdate(any());
        verify(snapshotRepository, never()).append(any());
    }

    @SuppressWarnings("unchecked")
    private void stubNewExecution() {
        doAnswer(invocation -> {
            Supplier<GovernanceActionResult> operation = invocation.getArgument(3);
            Function<GovernanceActionResult, PlatformCommandExecutionApi.SuccessFacts> factsFactory =
                    invocation.getArgument(4);
            GovernanceActionResult result = operation.get();
            successFacts.set(factsFactory.apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        }).when(commandExecutionApi).execute(any(), anyString(),
                eq(GovernanceActionResult.class), any(), any());
    }

    private static ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setRootId(ROOT_ID);
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S3");
        project.setAssignmentStatus("ASSIGNED");
        project.setVersion(VERSION);
        return project;
    }

    private static ProjectTreeVersionDO tree() {
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO();
        tree.setTenantId(TENANT_ID);
        tree.setRootProjectId(ROOT_ID);
        tree.setTreeVersion(7L);
        tree.setStatus("ACTIVE");
        return tree;
    }

    private static ProjectMemberAssignmentDO primary() {
        ProjectMemberAssignmentDO assignment = new ProjectMemberAssignmentDO();
        assignment.setProjectId(PROJECT_ID);
        assignment.setUserId(ACTOR_ID);
        assignment.setMemberRole("SERVICE_MANAGER_L1");
        assignment.setAssignmentType("PRIMARY");
        return assignment;
    }

    private static ProjectGovernanceGuardService.VerifiedGuard verifiedGuard() {
        return verifiedGuard(ROLLBACK);
    }

    private static ProjectGovernanceGuardService.VerifiedGuard verifiedGuard(
            ProjectGovernanceGuardService.GovernanceAction action) {
        LocalDateTime checkedAt = LocalDateTime.of(2026, 8, 25, 16, 0);
        List<ProjectGovernanceGuardResult.ProviderVersion> providers =
                ProjectGovernanceProviderRegistry.REQUIRED_PROVIDERS.stream()
                        .map(code -> new ProjectGovernanceGuardResult.ProviderVersion(
                                code, code + "_V1", "EMPTY", code + "_digest"))
                        .toList();
        ProjectGovernanceGuardTokenService.GuardClaims claims =
                new ProjectGovernanceGuardTokenService.GuardClaims(
                        TENANT_ID, PROJECT_ID, action.name(), VERSION, ROOT_ID, 7L, providers, checkedAt);
        ProjectGovernanceGuardResult latest = new ProjectGovernanceGuardResult(
                PROJECT_ID, VERSION, ROOT_ID, 7L, action.name(), true,
                "guard", providers, List.of(), checkedAt);
        return new ProjectGovernanceGuardService.VerifiedGuard(claims, latest);
    }

    private static RollbackProjectCommand command(String digest) {
        return new RollbackProjectCommand(PROJECT_ID, VERSION, "guard", "DELIVERY_SCOPE_CHANGED",
                "客户实施范围调整", "需要重新匹配属地服务经理", "idem-1", digest);
    }

    private static ExceptionCloseProjectCommand closeCommand(String digest) {
        return new ExceptionCloseProjectCommand(PROJECT_ID, VERSION, "close-guard",
                "CUSTOMER_TERMINATED", "客户不再继续实施", "客户书面确认终止实施",
                List.of(new ExceptionCloseProjectCommand.LegacyItem(
                        "DEVICE", "遗留设备移交", "客户项目经理", "OPEN")),
                "close-idem-1", digest);
    }

    private static ReopenProjectCommand reopenCommand(Long closeSnapshotId, String digest) {
        return new ReopenProjectCommand(PROJECT_ID, VERSION, "BUSINESS_RESUMED",
                "客户确认恢复实施", closeSnapshotId, "reopen-idem-1", digest);
    }

    private static ProjectStageSnapshotDO exceptionCloseSnapshot(Long id, String beforeStage) {
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setId(id);
        snapshot.setProjectId(PROJECT_ID);
        snapshot.setOperationType("EXCEPTION_CLOSE");
        snapshot.setBeforeStage(beforeStage);
        return snapshot;
    }

    private static ProjectGovernanceGuardService.Actor actor() {
        return new ProjectGovernanceGuardService.Actor(TENANT_ID, ACTOR_ID, "corr-1");
    }
}
