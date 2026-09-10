package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessStartFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageAdvanceUpdate;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceCommand;
import cn.iocoder.yudao.module.pms.project.service.stagegate.command.ProjectStageAdvanceResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectStageAdvanceApplicationServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 9L;
    private static final Long ACTOR_ID = 11L;

    private PlatformCommandExecutionApi commandExecutionApi;
    private ProjectStageGateProviderRegistry providerRegistry;
    private ProjectMasterMapper projectMapper;
    private ProjectMemberAssignmentMapper memberMapper;
    private ProjectStageInstanceMapper stageMapper;
    private ProjectGateInstanceMapper gateMapper;
    private ProjectGateReferenceInstanceMapper referenceMapper;
    private ProjectStageSnapshotMapper snapshotMapper;
    private ProjectStageSnapshotRepository snapshotRepository;
    private cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver graphResolver;
    private cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper graphMapper;
    private ProjectStageGateProcessOwnerApi processOwnerApi;
    private ProjectParticipantFactApi participantFactApi;
    private ProjectStageAdvanceApplicationService service;
    private AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        commandExecutionApi = mock(PlatformCommandExecutionApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        ProjectScopeApi projectScopeApi = mock(ProjectScopeApi.class);
        providerRegistry = mock(ProjectStageGateProviderRegistry.class);
        projectMapper = mock(ProjectMasterMapper.class);
        memberMapper = mock(ProjectMemberAssignmentMapper.class);
        when(memberMapper.selectActiveByUserForUpdate(any())).thenAnswer(invocation -> {
            cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery query = invocation.getArgument(0);
            return memberMapper.selectActiveForAssignmentState(
                    new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectAssignmentStateQuery(query.projectId(), query.effectiveAt()))
                    .stream().filter(row -> java.util.Objects.equals(row.getUserId(), query.userId())).toList();
        });
        stageMapper = mock(ProjectStageInstanceMapper.class);
        gateMapper = mock(ProjectGateInstanceMapper.class);
        referenceMapper = mock(ProjectGateReferenceInstanceMapper.class);
        snapshotMapper = mock(ProjectStageSnapshotMapper.class);
        snapshotRepository = mock(ProjectStageSnapshotRepository.class);
        successFacts = new AtomicReference<>();
        graphMapper = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper.class);
        graphResolver = new cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver(
                graphMapper, referenceMapper,
                new cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator(providerRegistry));
        processOwnerApi = mock(ProjectStageGateProcessOwnerApi.class);
        participantFactApi = mock(ProjectParticipantFactApi.class);
        service = new ProjectStageAdvanceApplicationService(commandExecutionApi, permissionApi, projectScopeApi,
                participantFactApi, processOwnerApi, providerRegistry,
                projectMapper, stageMapper, gateMapper, referenceMapper, memberMapper,
                snapshotMapper, snapshotRepository, graphResolver);

        when(permissionApi.hasAnyPermissions(ACTOR_ID, "pms:project:update")).thenReturn(true);
        ProjectScopeResult scope = new ProjectScopeResult(PROJECT_ID, 3L, Set.of(PROJECT_ID), Set.of());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope);
        doAnswer(invocation -> {
            Supplier<ProjectStageAdvanceResult> operation = invocation.getArgument(3);
            Function<ProjectStageAdvanceResult, PlatformCommandExecutionApi.SuccessFacts> factsFactory =
                    invocation.getArgument(4);
            ProjectStageAdvanceResult result = operation.get();
            successFacts.set(factsFactory.apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        }).when(commandExecutionApi).execute(any(), anyString(), eq(ProjectStageAdvanceResult.class), any(), any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void advancesFrozenTargetAfterOwnerFactsAreSatisfied() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);

        ProjectStageAdvanceResult result = service.advance(new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L,
                "advance-1", "a".repeat(64)), new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "corr-1"));

        assertEquals("S0", result.beforeStage());
        assertEquals("S4", result.afterStage());
        assertEquals(5, result.projectVersion());
        assertEquals(61L, result.stageSnapshotId());
        assertFalse(result.replayed());
        ArgumentCaptor<ProjectStageAdvanceUpdate> update = ArgumentCaptor.forClass(ProjectStageAdvanceUpdate.class);
        verify(projectMapper).advanceStageIfMatch(update.capture());
        assertEquals("S0", update.getValue().expectedCurrentStage());
        assertEquals("S4", update.getValue().targetStage());
        assertEquals(1, successFacts.get().businessEvents().size());
        PlatformCommandExecutionApi.BusinessEvent event = successFacts.get().businessEvents().getFirst();
        assertEquals(result.operationId(), event.eventId());
        assertEquals(result.operationId(), JsonUtils.parseObject(event.eventPayload(), Map.class).get("eventId"));
    }

    @Test
    void unsatisfiedOwnerFactWritesNothing() {
        stubLockedContext(ProjectStageGateOutcome.UNSATISFIED);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.advance(new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L,
                                "advance-2", "b".repeat(64)),
                        new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "corr-2")));

        verify(gateMapper, never()).updateStatusIfMatch(any());
        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).advanceStageIfMatch(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void targetEntryGateBlocksAllWritesAndUsesTargetIdentity() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        var exit = graphMapper.selectGatesForUpdate(null).getFirst();
        var entry = new ProjectGateInstanceDO().setId(32L).setProjectId(PROJECT_ID)
                .setGateCode("G-S4-ENTRY").setGateType("ENTRY").setStageCode("S4").setStatus("PENDING").setVersion(0);
        var exitRef = referenceMapper.selectOrderedForUpdate(null).getFirst();
        var entryRef = new ProjectGateReferenceInstanceDO().setId(42L).setGateId(32L)
                .setRefType("TASK").setRefCode("T-ENTRY").setVersion(0);
        when(graphMapper.selectGatesForUpdate(any())).thenReturn(List.of(exit, entry));
        when(referenceMapper.selectOrderedForUpdate(any())).thenReturn(List.of(exitRef, entryRef));
        when(providerRegistry.lockAndRevalidate(any(), org.mockito.ArgumentMatchers.argThat(query ->
                query != null && "T-ENTRY".equals(query.refCode())))).thenReturn(new ProjectStageGateFact(
                ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK, "TASK", "52", "IN_PROGRESS", "1",
                ProjectStageGateOutcome.UNSATISFIED, "ENTRY_NOT_READY"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.advance(
                new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L, "entry", "e".repeat(64)),
                new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "entry")));

        verify(providerRegistry).lockAndRevalidate(any(), org.mockito.ArgumentMatchers.argThat(query ->
                "T-ENTRY".equals(query.refCode()) && "S4".equals(query.currentStageCode())));
        verify(gateMapper, never()).updateStatusIfMatch(any());
        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).advanceStageIfMatch(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void terminalAndUnfinishedNativeTasksNeverWriteAdvanceFacts() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        var task = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO();
        task.setStageCode("S0"); task.setStatus("IN_PROGRESS");
        when(graphMapper.selectTasksForUpdate(any())).thenReturn(List.of(task));
        var actor = new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "blocked");
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.advance(
                new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L, "tasks", "e".repeat(64)), actor));
        var stages = graphMapper.selectStagesForUpdate(null);
        stages.getFirst().setStatus("DONE"); stages.getLast().setStatus("ACTIVE");
        projectMapper.selectByIdForUpdate(PROJECT_ID).setCurrentStage("S4");
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.advance(
                new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S4", 3L, "terminal", "f".repeat(64)), actor));
        assertEquals("ACTIVE", projectMapper.selectByIdForUpdate(PROJECT_ID).getLifecycleStatus());
        verify(gateMapper, never()).updateStatusIfMatch(any());
        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).advanceStageIfMatch(any());
        verify(snapshotRepository, never()).append(any());
    }

    @Test
    void nonPrimaryManagerCanAdvanceButMissingMemberCannot() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        projectMapper.selectByIdForUpdate(PROJECT_ID).setManagerId(99L);
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(
                responsibility("PROJECT_MANAGER", 99L), responsibility("SERVICE_MANAGER_L1", 12L)));
        ProjectMemberAssignmentDO member = new ProjectMemberAssignmentDO();
        member.setUserId(ACTOR_ID);
        member.setMemberRole("PROJECT_MANAGER");
        member.setAssignmentType("COLLABORATOR");
        when(memberMapper.selectParticipantFactsForUpdate(any())).thenReturn(List.of(member));
        var command = new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L,
                "secondary", "d".repeat(64));
        var actor = new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "corr-secondary");
        assertEquals("S4", service.advance(command, actor).afterStage());
        when(memberMapper.selectParticipantFactsForUpdate(any())).thenReturn(List.of());
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> service.advance(command, actor));
    }

    @Test
    void targetEntryProcessCanSelectAndStartWithConsistentStageIdentity() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        var entry = new ProjectGateInstanceDO().setId(32L).setProjectId(PROJECT_ID)
                .setGateCode("G-S4-ENTRY").setGateType("ENTRY").setStageCode("S4").setStatus("PENDING").setVersion(0);
        var reference = new ProjectGateReferenceInstanceDO().setId(42L).setGateId(32L)
                .setRefType("PROCESS").setRefCode("entry-process").setVersion(0);
        var stages = graphMapper.selectStagesForUpdate(null);
        when(graphMapper.selectStages(any())).thenReturn(stages);
        when(graphMapper.selectGates(any())).thenReturn(List.of(entry));
        when(graphMapper.selectGatesForUpdate(any())).thenReturn(List.of(entry));
        when(referenceMapper.selectOrdered(any())).thenReturn(List.of(reference));
        when(referenceMapper.selectOrderedForUpdate(any())).thenReturn(List.of(reference));
        var project = projectMapper.selectByIdForUpdate(PROJECT_ID);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        var actor = new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "entry-start");
        service.listDefinitions(PROJECT_ID, 42L, actor);
        verify(processOwnerApi).listSelectableDefinitions(any());
        doAnswer(invocation -> {
            Supplier<ProjectStageGateProcessStartFact> operation = invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW, operation.get());
        }).when(commandExecutionApi).execute(any(), anyString(), eq(ProjectStageGateProcessStartFact.class), any(), any());
        when(processOwnerApi.startProcess(any())).thenReturn(new ProjectStageGateProcessStartFact(
                "pi-entry", "def-entry", "entry-process", "PROJECT_STAGE_GATE:42", "STARTED"));

        assertEquals("STARTED", service.startProcess(PROJECT_ID, 42L, 4, "def-entry", "entry-start",
                "a".repeat(64), actor).outcome());
        verify(processOwnerApi).startProcess(org.mockito.ArgumentMatchers.argThat(command ->
                "S4".equals(command.currentStageCode()) && command.gateReferenceId().equals(42L)));
        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).advanceStageIfMatch(any());
    }

    @Test
    void replaysProcessStartFromTheAtomicIdempotencyBoundary() {
        ProjectStageGateProcessStartFact stored = new ProjectStageGateProcessStartFact(
                "pi-1", "def-1", "gate-process", "PROJECT_STAGE_GATE:41", "STARTED");
        when(commandExecutionApi.execute(any(), anyString(), eq(ProjectStageGateProcessStartFact.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, stored));

        ProjectStageGateProcessStartFact replayed = service.startProcess(PROJECT_ID, 41L, 4, null,
                "process-1", "c".repeat(64),
                new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "corr-3"));

        assertEquals("pi-1", replayed.processInstanceId());
        assertEquals("REPLAYED", replayed.outcome());
        verify(projectMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void s0AdvanceWithOnlyPmOrOnlySmWritesNothing() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(responsibility("PROJECT_MANAGER", ACTOR_ID)));
        assertS0AssignmentRejected("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        // Actor authorization is independent of whether the project's current primary PM exists.
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(responsibility("SERVICE_MANAGER_L1", 12L)));
        assertS0AssignmentRejected("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
        verifyNoAdvanceWrites();
    }

    @Test
    void s0AdvanceRevalidatesResponsibilitiesUnderProjectLockAfterSuccessfulPreview() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        var primaryPm = responsibility("PROJECT_MANAGER", ACTOR_ID);
        var primarySm = responsibility("SERVICE_MANAGER_L2", 12L);
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(primaryPm, primarySm));
        var project = projectMapper.selectByIdForUpdate(PROJECT_ID);
        org.junit.jupiter.api.Assertions.assertNull(ProjectStageReadinessService.s0AssignmentUnmetReason(project, memberMapper));
        // Snapshot still reports SM active, but the current locking read observes the closed interval.
        org.mockito.Mockito.doReturn(List.of()).when(memberMapper).selectActiveByUserForUpdate(org.mockito.ArgumentMatchers.argThat(query ->
                query != null && query.userId().equals(12L)));
        org.mockito.Mockito.clearInvocations(projectMapper, memberMapper);
        assertS0AssignmentRejected("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        var order = org.mockito.Mockito.inOrder(projectMapper, memberMapper);
        order.verify(projectMapper).selectByIdForUpdate(PROJECT_ID);
        order.verify(memberMapper).selectParticipantFactsForUpdate(any());
        order.verify(memberMapper).selectActiveByUserForUpdate(org.mockito.ArgumentMatchers.argThat(query -> query.userId().equals(ACTOR_ID)));
        order.verify(memberMapper).selectActiveByUserForUpdate(org.mockito.ArgumentMatchers.argThat(query -> query.userId().equals(12L)));
        verifyNoAdvanceWrites();
    }

    @Test
    void s0AdvanceRejectsForeignInactiveAndMismatchedPrimaryFactsEvenWhenAssigned() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        var primaryPm = responsibility("PROJECT_MANAGER", ACTOR_ID);
        var primarySm = responsibility("SERVICE_MANAGER_L1", 12L);
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(primaryPm, primarySm));
        primarySm.setTenantId(8L);
        assertS0AssignmentRejected("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setTenantId(TENANT_ID); primarySm.setStatus("INACTIVE");
        assertS0AssignmentRejected("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setStatus("ACTIVE"); primarySm.setAssignmentType("COLLABORATOR");
        assertS0AssignmentRejected("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setAssignmentType("PRIMARY"); primaryPm.setUserId(99L);
        assertS0AssignmentRejected("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
        primaryPm.setUserId(ACTOR_ID); primaryPm.setTenantId(8L);
        assertS0AssignmentRejected("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
        primaryPm.setTenantId(TENANT_ID); primaryPm.setEffectiveTo(java.time.LocalDateTime.now().minusSeconds(1));
        assertS0AssignmentRejected("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
        verifyNoAdvanceWrites();
    }

    @Test
    void s0AdvanceRequiresAssignedStateEvenWhenBothResponsibilitiesExist() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        projectMapper.selectByIdForUpdate(PROJECT_ID).setAssignmentStatus("UNASSIGNED");
        assertS0AssignmentRejected("S0_ASSIGNMENT_STATUS_NOT_ASSIGNED");
        verifyNoAdvanceWrites();
    }

    @Test
    void nonS0AdvanceDoesNotAddAssignmentPrerequisite() {
        stubLockedContext(ProjectStageGateOutcome.SATISFIED);
        var project = projectMapper.selectByIdForUpdate(PROJECT_ID);
        project.setCurrentStage("S4"); project.setManagerId(null); project.setAssignmentStatus("UNASSIGNED");
        var stages = graphMapper.selectStagesForUpdate(null);
        stages.getFirst().setStatus("DONE"); stages.getLast().setStatus("ACTIVE"); stages.getLast().setTerminalNode(false);
        var terminal = new ProjectStageInstanceDO().setId(23L).setProjectId(PROJECT_ID).setStageCode("S6")
                .setStatus("PENDING").setVersion(0).setGraphVersion(1L).setDefinitionRevisionId(203L)
                .setStartNode(false).setTerminalNode(true);
        terminal.setTenantId(TENANT_ID);
        when(graphMapper.selectStagesForUpdate(any())).thenReturn(List.of(stages.getFirst(), stages.getLast(), terminal));
        var contracts = graphMapper.selectContracts(null);
        var terminalContract = new cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO();
        terminalContract.setTenantId(TENANT_ID); terminalContract.setProjectId(PROJECT_ID); terminalContract.setStageId(23L);
        terminalContract.setGraphVersion(1L); terminalContract.setDefinitionRevisionId(203L);
        terminalContract.setCompletionRuleRevisionId(100L); terminalContract.setDefinitionSnapshot(contracts.getFirst().getDefinitionSnapshot());
        when(graphMapper.selectContracts(any())).thenReturn(List.of(contracts.getFirst(), contracts.getLast(), terminalContract));
        var original = graphMapper.selectTransitions(null).getFirst();
        var edge = new cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO();
        edge.setTenantId(TENANT_ID); edge.setProjectId(PROJECT_ID); edge.setGraphVersion(1L);
        edge.setFromStageId(22L); edge.setToStageId(23L); edge.setTransitionCode("S4-S6"); edge.setPriority(1); edge.setIsDefault(false);
        when(graphMapper.selectTransitions(any())).thenReturn(List.of(original, edge));
        var result = service.advance(new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S4", 3L, "non-s0", "a".repeat(64)),
                new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "non-s0"));
        assertEquals("S6", result.afterStage());
        verify(memberMapper, never()).selectActiveForAssignmentState(any());
    }

    private void assertS0AssignmentRejected(String reason) {
        var failure = org.junit.jupiter.api.Assertions.assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.advance(new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L, "s0-guard", "a".repeat(64)),
                        new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "s0-guard")));
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains(reason));
    }

    private void verifyNoAdvanceWrites() {
        verify(gateMapper, never()).updateStatusIfMatch(any());
        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).advanceStageIfMatch(any());
        verify(snapshotRepository, never()).append(any());
        org.junit.jupiter.api.Assertions.assertNull(successFacts.get());
    }

    private static ProjectMemberAssignmentDO responsibility(String role, Long userId) {
        var member = new ProjectMemberAssignmentDO();
        member.setTenantId(TENANT_ID); member.setProjectId(PROJECT_ID); member.setUserId(userId);
        member.setMemberRole(role); member.setAssignmentType("PRIMARY"); member.setStatus("ACTIVE");
        return member;
    }

    private void stubLockedContext(ProjectStageGateOutcome outcome) {
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(
                responsibility("PROJECT_MANAGER", ACTOR_ID), responsibility("SERVICE_MANAGER_L1", 12L)));
        ProjectMemberAssignmentDO member = new ProjectMemberAssignmentDO();
        member.setProjectId(PROJECT_ID);
        member.setUserId(ACTOR_ID);
        member.setMemberRole("PROJECT_MANAGER");
        member.setAssignmentType("PRIMARY");
        when(memberMapper.selectParticipantFactsForUpdate(any())).thenReturn(List.of(member));
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setManagerId(ACTOR_ID);
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S0");
        project.setAssignmentStatus("ASSIGNED");
        project.setVersion(4);
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(project);

        ProjectStageInstanceDO current = new ProjectStageInstanceDO().setId(21L).setProjectId(PROJECT_ID)
                .setStageCode("S0").setSortOrder(0).setStatus("ACTIVE").setVersion(1);
        ProjectStageInstanceDO next = new ProjectStageInstanceDO().setId(22L).setProjectId(PROJECT_ID)
                .setStageCode("S4").setSortOrder(1).setStatus("PENDING").setVersion(0);
        next.setSortOrder(99);
        current.setTenantId(TENANT_ID); next.setTenantId(TENANT_ID);
        current.setGraphVersion(1L); next.setGraphVersion(1L);
        current.setDefinitionRevisionId(201L); next.setDefinitionRevisionId(202L);
        current.setStartNode(true); current.setTerminalNode(false);
        next.setStartNode(false); next.setTerminalNode(true);
        when(graphMapper.selectStagesForUpdate(any())).thenReturn(List.of(current, next));
        when(graphMapper.selectContracts(any())).thenReturn(List.of(current, next).stream().map(stage -> {
            var contract = new cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO();
            contract.setTenantId(TENANT_ID); contract.setProjectId(PROJECT_ID); contract.setStageId(stage.getId());
            contract.setGraphVersion(1L); contract.setDefinitionRevisionId(stage.getDefinitionRevisionId());
            contract.setCompletionRuleRevisionId(100L);
            contract.setDefinitionSnapshot("[{\"definition\":{\"id\":100,\"definitionKind\":\"COMPLETION_RULE\",\"schemaVersion\":1,\"payload\":{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}}}]");
            return contract;
        }).toList());
        var edge = new cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO();
        edge.setTenantId(TENANT_ID); edge.setProjectId(PROJECT_ID); edge.setGraphVersion(1L);
        edge.setFromStageId(current.getId()); edge.setToStageId(next.getId()); edge.setTransitionCode("S0-S4");
        edge.setPriority(1); edge.setIsDefault(false);
        when(graphMapper.selectTransitions(any())).thenReturn(List.of(edge));

        ProjectGateInstanceDO gate = new ProjectGateInstanceDO().setId(31L).setProjectId(PROJECT_ID)
                .setGateCode("G-S0-EXIT").setGateType("EXIT").setStageCode("S0")
                .setStatus("PENDING").setVersion(0);
        ProjectGateReferenceInstanceDO reference = new ProjectGateReferenceInstanceDO().setId(41L).setGateId(31L)
                .setRefType("TASK").setRefCode("T-S0").setVersion(0);
        when(graphMapper.selectGatesForUpdate(any())).thenReturn(List.of(gate));
        when(gateMapper.selectExitGatesForUpdate(any())).thenReturn(List.of(gate));
        when(referenceMapper.selectOrderedForUpdate(any())).thenReturn(List.of(reference));
        when(providerRegistry.lockAndRevalidate(eq(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK), any()))
                .thenReturn(new ProjectStageGateFact(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK, "TASK",
                        "51", outcome == ProjectStageGateOutcome.SATISFIED ? "DONE" : "IN_PROGRESS", "2",
                        outcome, outcome == ProjectStageGateOutcome.SATISFIED ? null : "TASK_NOT_DONE"));
        when(gateMapper.updateStatusIfMatch(any())).thenReturn(1);
        when(stageMapper.updateStatusIfMatch(any())).thenReturn(1);
        when(projectMapper.advanceStageIfMatch(any())).thenReturn(1);
        when(snapshotMapper.selectNextSnapshotNo(any())).thenReturn(1);
        when(snapshotRepository.append(any())).thenAnswer(invocation -> {
            ProjectStageSnapshotDO snapshot = invocation.getArgument(0);
            snapshot.setId(61L);
            return 1;
        });
    }
}
