package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphResolver;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectStageReadinessServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 9L;
    private static final Long ACTOR_ID = 11L;
    private static final String SNAPSHOT = "[{\"definition\":{\"id\":100,\"definitionKind\":\"COMPLETION_RULE\",\"schemaVersion\":1,\"payload\":{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}}}]";

    private ProjectStageGateProviderRegistry providerRegistry;
    private ProjectRuntimeGraphMapper graphMapper;
    private ProjectGateReferenceInstanceMapper referenceMapper;
    private ProjectMemberAssignmentMapper memberMapper;
    private ProjectMemberAssignmentDO primaryPm;
    private ProjectMemberAssignmentDO primarySm;
    private ProjectStageReadinessService service;
    private ProjectMasterDO project;
    private List<ProjectStageInstanceDO> stages;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        referenceMapper = mock(ProjectGateReferenceInstanceMapper.class);
        providerRegistry = mock(ProjectStageGateProviderRegistry.class);
        graphMapper = mock(ProjectRuntimeGraphMapper.class);
        memberMapper = mock(ProjectMemberAssignmentMapper.class);
        primaryPm = member("PROJECT_MANAGER", ACTOR_ID);
        primarySm = member("SERVICE_MANAGER_L1", 12L);
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(primaryPm, primarySm));
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantFactApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        var graphResolver = new ProjectRuntimeGraphResolver(graphMapper, referenceMapper,
                new ProjectRuntimeRuleEvaluator(providerRegistry));
        service = new ProjectStageReadinessService(graphResolver, projectMapper,
                mock(ProjectStageInstanceMapper.class), mock(ProjectGateInstanceMapper.class), referenceMapper,
                providerRegistry, scopeApi, participantFactApi, permissionApi, memberMapper);

        project = new ProjectMasterDO();
        project.setId(PROJECT_ID); project.setTenantId(TENANT_ID);
        project.setManagerId(ACTOR_ID); project.setAssignmentStatus("ASSIGNED");
        project.setLifecycleStatus("ACTIVE"); project.setCurrentStage("S0"); project.setVersion(4);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        stages = List.of(stage(21L, "S0", 0, true, false), stage(22L, "S4", 99, false, false),
                stage(23L, "S6", 1, false, true));
        when(graphMapper.selectStages(any())).thenReturn(stages);
        when(graphMapper.selectContracts(any())).thenReturn(stages.stream().map(stage -> {
            var contract = new ProjectStageExecutionContractDO();
            contract.setTenantId(TENANT_ID); contract.setProjectId(PROJECT_ID); contract.setStageId(stage.getId());
            contract.setGraphVersion(1L); contract.setDefinitionRevisionId(stage.getDefinitionRevisionId());
            contract.setCompletionRuleRevisionId(100L); contract.setDefinitionSnapshot(SNAPSHOT);
            return contract;
        }).toList());
        when(graphMapper.selectTransitions(any())).thenReturn(List.of(edge(21L, 22L), edge(22L, 23L)));
        ProjectGateInstanceDO gate = new ProjectGateInstanceDO().setId(31L).setGateCode("G-S0-EXIT")
                .setGateType("EXIT").setStageCode("S0").setStatus("PENDING").setVersion(0);
        when(graphMapper.selectGates(any())).thenReturn(List.of(gate));
        when(referenceMapper.selectOrdered(any())).thenReturn(List.of(
                new ProjectGateReferenceInstanceDO().setId(41L).setGateId(31L)
                        .setRefType("PROCESS").setRefCode("gate-process").setVersion(0)));
        ProjectScopeResult scope = new ProjectScopeResult(PROJECT_ID, 3L, Set.of(PROJECT_ID), Set.of());
        when(scopeApi.resolveCurrent(any())).thenReturn(scope);
        when(permissionApi.hasAnyPermissions(anyLong(), any())).thenReturn(true);
        when(participantFactApi.inspect(any())).thenReturn(new ProjectParticipantFact(PROJECT_ID, ACTOR_ID,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY", "ACTIVE", "S0", 4, 4L));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void onlyOffersProcessStartWhenTheGateHasNotStarted() {
        when(providerRegistry.lockAndRevalidate(any(), any())).thenReturn(processFact("PROCESS_RUNNING"));
        ProjectStageReadinessResult running = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertFalse(running.advanceAllowed());
        assertEquals(List.of(), running.gates().getFirst().references().getFirst().allowedActions());
        when(providerRegistry.lockAndRevalidate(any(), any())).thenReturn(processFact("PROCESS_NOT_STARTED"));
        ProjectStageReadinessResult notStarted = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertEquals(List.of("START_PROCESS"), notStarted.gates().getFirst().references().getFirst().allowedActions());
    }

    @Test
    void previewFollowsFrozenEdgeWithoutLockingAllRuntimeRows() {
        when(providerRegistry.lockAndRevalidate(any(), any())).thenReturn(new ProjectStageGateFact(
                ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS, "PROCESS", "pi-1", "DONE", "1",
                ProjectStageGateOutcome.SATISFIED, null));
        var result = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertTrue(result.advanceAllowed());
        assertEquals("S4", result.nextStage());
        verify(graphMapper, never()).selectStagesForUpdate(any());
        verify(graphMapper, never()).selectTasksForUpdate(any());
        verify(graphMapper, never()).selectGatesForUpdate(any());
        verify(referenceMapper, never()).selectOrderedForUpdate(any());
    }

    @Test
    void targetEntryProcessBlocksReadinessUsingTargetStageIdentity() {
        var entry = new ProjectGateInstanceDO().setId(32L).setGateCode("G-S4-ENTRY")
                .setGateType("ENTRY").setStageCode("S4").setStatus("PENDING").setVersion(0);
        when(graphMapper.selectGates(any())).thenReturn(List.of(entry));
        when(referenceMapper.selectOrdered(any())).thenReturn(List.of(
                new ProjectGateReferenceInstanceDO().setId(42L).setGateId(32L)
                        .setRefType("PROCESS").setRefCode("entry-process").setVersion(0)));
        when(providerRegistry.lockAndRevalidate(any(), any())).thenReturn(processFact("PROCESS_NOT_STARTED"));
        var result = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertFalse(result.advanceAllowed());
        assertEquals("S4", result.nextStage());
        verify(providerRegistry).lockAndRevalidate(any(), argThat(query -> "S4".equals(query.currentStageCode())
                && "entry-process".equals(query.refCode())));
    }

    @Test
    void terminalIsNotAdvanceOrClosureAndMissingGraphFailsClosed() {
        project.setCurrentStage("S6");
        stages.getFirst().setStatus("DONE"); stages.getLast().setStatus("ACTIVE");
        var terminal = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertFalse(terminal.advanceAllowed());
        assertNull(terminal.nextStage());
        assertEquals("TERMINAL", terminal.guidance());
        assertEquals("ACTIVE", project.getLifecycleStatus());
        stages.getFirst().setGraphVersion(null);
        assertThrows(RuntimeException.class, () -> service.evaluate(PROJECT_ID, ACTOR_ID));
    }

    @Test
    void rejectsAStagePairWhoseTargetIsAlreadyActive() {
        stages.get(1).setStatus("ACTIVE");
        assertThrows(RuntimeException.class, () -> service.evaluate(PROJECT_ID, ACTOR_ID));
    }

    @Test
    void s0WithoutTasksRequiresBothRealPrimaryManagersInPreview() {
        when(graphMapper.selectGates(any())).thenReturn(List.of());
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(primaryPm));
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of(primarySm));
        assertAssignmentBlocked("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
        when(memberMapper.selectActiveForAssignmentState(any())).thenReturn(List.of());
        assertAssignmentBlocked("S0_PRIMARY_MANAGERS_REQUIRED");
    }

    @Test
    void s0PreviewRejectsExpiredFutureInactiveDeletedAndForeignResponsibilities() {
        when(graphMapper.selectGates(any())).thenReturn(List.of());
        primarySm.setEffectiveTo(java.time.LocalDateTime.now().minusSeconds(1));
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setEffectiveTo(null);
        primarySm.setEffectiveFrom(java.time.LocalDateTime.now().plusDays(1));
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setEffectiveFrom(null); primarySm.setStatus("INACTIVE");
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setStatus("ACTIVE"); primarySm.setDeleted(true);
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setDeleted(false); primarySm.setTenantId(8L);
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setTenantId(TENANT_ID); primarySm.setProjectId(99L);
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setProjectId(PROJECT_ID); primaryPm.setEffectiveTo(java.time.LocalDateTime.now().minusSeconds(1));
        assertAssignmentBlocked("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
    }

    @Test
    void s0PreviewRequiresCurrentPrimaryPointerAndAssignedStateNotProjectionLabels() {
        when(graphMapper.selectGates(any())).thenReturn(List.of());
        project.setManagerId(99L);
        assertAssignmentBlocked("S0_PRIMARY_PROJECT_MANAGER_REQUIRED");
        project.setManagerId(ACTOR_ID); primarySm.setAssignmentType("COLLABORATOR");
        assertAssignmentBlocked("S0_PRIMARY_SERVICE_MANAGER_REQUIRED");
        primarySm.setAssignmentType(null); primarySm.setMemberRole("SERVICE_MANAGER_L2");
        primaryPm.setAssignmentType("COLLABORATOR"); // A primary switch preserves the member's original label.
        project.setAssignmentStatus("UNASSIGNED");
        assertAssignmentBlocked("S0_ASSIGNMENT_STATUS_NOT_ASSIGNED");
        project.setAssignmentStatus("ASSIGNED");
        var result = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertTrue(result.advanceAllowed());
        assertEquals("S4", result.nextStage());
        verify(graphMapper, never()).selectTasksForUpdate(any());
    }

    @Test
    void nonS0PreviewDoesNotAddAssignmentPrerequisite() {
        when(graphMapper.selectGates(any())).thenReturn(List.of());
        project.setCurrentStage("S4"); project.setManagerId(null); project.setAssignmentStatus("UNASSIGNED");
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("ACTIVE");
        assertTrue(service.evaluate(PROJECT_ID, ACTOR_ID).advanceAllowed());
        verifyNoInteractions(memberMapper);
    }

    private void assertAssignmentBlocked(String reason) {
        var result = service.evaluate(PROJECT_ID, ACTOR_ID);
        assertFalse(result.advanceAllowed());
        assertEquals(reason, result.guidance());
        assertEquals("S4", result.nextStage());
    }

    private static ProjectMemberAssignmentDO member(String role, Long userId) {
        var member = new ProjectMemberAssignmentDO();
        member.setTenantId(TENANT_ID); member.setProjectId(PROJECT_ID); member.setUserId(userId);
        member.setMemberRole(role); member.setAssignmentType("PRIMARY"); member.setStatus("ACTIVE");
        return member;
    }

    private static ProjectStageInstanceDO stage(Long id, String code, int sort, boolean start, boolean terminal) {
        var stage = new ProjectStageInstanceDO().setId(id).setProjectId(PROJECT_ID).setStageCode(code)
                .setSortOrder(sort).setStatus(start ? "ACTIVE" : "PENDING").setGraphVersion(1L)
                .setDefinitionRevisionId(id + 200).setStartNode(start).setTerminalNode(terminal);
        stage.setTenantId(TENANT_ID);
        return stage;
    }

    private static ProjectStageTransitionDO edge(Long from, Long to) {
        var edge = new ProjectStageTransitionDO();
        edge.setTenantId(TENANT_ID); edge.setProjectId(PROJECT_ID); edge.setGraphVersion(1L);
        edge.setFromStageId(from); edge.setToStageId(to); edge.setTransitionCode("E" + from + to);
        edge.setPriority(1); edge.setIsDefault(false);
        return edge;
    }

    private static ProjectStageGateFact processFact(String unmetCode) {
        return new ProjectStageGateFact(ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS, "PROCESS",
                "pi-1", "def-1", "1", ProjectStageGateOutcome.UNSATISFIED, unmetCode);
    }
}
