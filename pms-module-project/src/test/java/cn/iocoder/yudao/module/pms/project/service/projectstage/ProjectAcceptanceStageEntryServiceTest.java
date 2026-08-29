package cn.iocoder.yudao.module.pms.project.service.projectstage;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.AcceptanceScopeBindingApi;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingFact;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeBindingResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.repository.projectgovernance.ProjectStageSnapshotRepository;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_PHASE_SEQUENCE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAcceptanceStageEntryServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long ACTOR_ID = 99L;

    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private PermissionApi permissionApi;
    @Mock private ProjectTreeScopeService treeScopeService;
    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectStageInstanceMapper stageMapper;
    @Mock private ProjectStageSnapshotMapper snapshotMapper;
    @Mock private ProjectStageSnapshotRepository snapshotRepository;
    @Mock private AcceptanceScopeBindingApi bindingApi;
    private ProjectAcceptanceStageEntryService service;
    private AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        service = new ProjectAcceptanceStageEntryService(commandExecutionApi, permissionApi, treeScopeService,
                projectMapper, stageMapper, snapshotMapper, snapshotRepository, bindingApi);
        successFacts = new AtomicReference<>();
        stubNewExecution();
        when(permissionApi.hasAnyPermissions(ACTOR_ID,
                ProjectAcceptanceStageEntryService.PERMISSION_UPDATE)).thenReturn(true);
        when(treeScopeService.lockAndRevalidate(any())).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(PROJECT_ID, 12L,
                        Set.of(PROJECT_ID), Set.of(), Set.of()));
        when(projectMapper.selectByIdForUpdate(PROJECT_ID)).thenReturn(project());
        when(stageMapper.selectListForTransition(any())).thenReturn(List.of(stage(100L, "S4", 4, "DONE", 2),
                stage(101L, "S5", 5, "PENDING", 0)));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBindAllCurrentScopesBeforeActivatingAcceptanceStage() {
        stubSnapshot();
        when(bindingApi.bindForStageEntry(any())).thenReturn(new AcceptanceScopeBindingResult(false, 1,
                List.of(new AcceptanceScopeBindingFact(800L, 700L, 400L, 7L,
                        "PROJECT_STAGE_ENTRY", 1))));
        stubSuccessfulStateUpdates();

        ProjectAcceptanceStageEntryResult result = service.enter(command(), actor());

        assertEquals("S4", result.beforeStageCode());
        assertEquals("S5", result.acceptanceStageCode());
        assertEquals(4, result.projectVersion());
        assertEquals(700L, result.projectStageSnapshotId());
        assertEquals(1, result.bindingCount());
        assertFalse(result.replayed());
        ArgumentCaptor<ProjectStageSnapshotDO> snapshot = ArgumentCaptor.forClass(ProjectStageSnapshotDO.class);
        verify(snapshotRepository).append(snapshot.capture());
        assertEquals("STAGE_ENTRY", snapshot.getValue().getOperationType());
        assertEquals("S4", snapshot.getValue().getBeforeStage());
        assertEquals("S5", snapshot.getValue().getAfterStage());
        assertEquals(12L, snapshot.getValue().getTreeVersion());
        verify(bindingApi).bindForStageEntry(argThat(command -> command.projectStageSnapshotId().equals(700L)
                && command.fromStageCode().equals("S4") && command.acceptanceStageCode().equals("S5")));
        verify(stageMapper).updateStatusIfMatch(argThat(update -> update.stageId().equals(101L)
                && update.expectedStatus().equals("PENDING") && update.targetStatus().equals("ACTIVE")));
        verify(projectMapper).updateGovernanceStateIfMatch(argThat(update ->
                update.currentStage().equals("S5") && update.expectedVersion().equals(3)));
        InOrder writeOrder = inOrder(snapshotRepository, bindingApi, stageMapper, projectMapper);
        writeOrder.verify(snapshotRepository).append(any());
        writeOrder.verify(bindingApi).bindForStageEntry(any());
        writeOrder.verify(stageMapper).updateStatusIfMatch(any());
        writeOrder.verify(projectMapper).updateGovernanceStateIfMatch(any());
        assertEquals(1, successFacts.get().businessEvents().size());
        var event = successFacts.get().businessEvents().getFirst();
        assertEquals(result.operationId(), event.eventId());
        assertEquals("ProjectStageChanged", event.eventType());
        assertTrue(event.eventPayload().contains("\"stageSnapshotId\":700"));
    }

    @Test
    void shouldEnterAcceptanceStageWhenProjectHasNoCurrentDeliveryScopes() {
        stubSnapshot();
        when(bindingApi.bindForStageEntry(any())).thenReturn(
                new AcceptanceScopeBindingResult(false, 1, List.of()));
        stubSuccessfulStateUpdates();

        ProjectAcceptanceStageEntryResult result = service.enter(command(), actor());

        assertEquals(0, result.bindingCount());
        verify(stageMapper).updateStatusIfMatch(any());
        verify(projectMapper).updateGovernanceStateIfMatch(any());
    }

    @Test
    void shouldRejectEntryUntilCurrentStageIsDoneWithoutWrites() {
        when(stageMapper.selectListForTransition(any())).thenReturn(List.of(stage(100L, "S4", 4, "ACTIVE", 2),
                stage(101L, "S5", 5, "PENDING", 0)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.enter(command(), actor()));

        assertEquals(PROJECT_PHASE_SEQUENCE_INVALID.getCode(), error.getCode());
        verify(snapshotRepository, never()).append(any());
        verify(bindingApi, never()).bindForStageEntry(any());
        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
    }

    @Test
    void shouldNotAdvanceProjectWhenBindingFails() {
        stubSnapshot();
        when(bindingApi.bindForStageEntry(any())).thenThrow(new IllegalStateException("binding failed"));

        assertThrows(IllegalStateException.class, () -> service.enter(command(), actor()));

        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
    }

    @Test
    void shouldRejectInconsistentAcceptanceFactVersionWithoutAdvancingProject() {
        stubSnapshot();
        when(bindingApi.bindForStageEntry(any())).thenReturn(new AcceptanceScopeBindingResult(false, 2,
                List.of(new AcceptanceScopeBindingFact(800L, 700L, 400L, 7L,
                        "PROJECT_STAGE_ENTRY", 1))));

        assertThrows(ServiceException.class, () -> service.enter(command(), actor()));

        verify(stageMapper, never()).updateStatusIfMatch(any());
        verify(projectMapper, never()).updateGovernanceStateIfMatch(any());
    }

    @SuppressWarnings("unchecked")
    private void stubNewExecution() {
        doAnswer(invocation -> {
            Supplier<ProjectAcceptanceStageEntryResult> operation = invocation.getArgument(3);
            Function<ProjectAcceptanceStageEntryResult, PlatformCommandExecutionApi.SuccessFacts> factsFactory =
                    invocation.getArgument(4);
            ProjectAcceptanceStageEntryResult result = operation.get();
            successFacts.set(factsFactory.apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        }).when(commandExecutionApi).execute(any(), anyString(),
                eq(ProjectAcceptanceStageEntryResult.class), any(), any());
    }

    private void stubSnapshot() {
        when(snapshotMapper.selectNextSnapshotNo(any())).thenReturn(1);
        when(snapshotRepository.append(any())).thenAnswer(invocation -> {
            invocation.<ProjectStageSnapshotDO>getArgument(0).setId(700L);
            return 1;
        });
    }

    private void stubSuccessfulStateUpdates() {
        when(stageMapper.updateStatusIfMatch(any())).thenReturn(1);
        when(projectMapper.updateGovernanceStateIfMatch(any())).thenReturn(1);
    }

    private ProjectAcceptanceStageEntryCommand command() {
        return new ProjectAcceptanceStageEntryCommand(PROJECT_ID, 3, 12L, "idem-1", "a".repeat(64));
    }

    private ProjectAcceptanceStageEntryService.Actor actor() {
        return new ProjectAcceptanceStageEntryService.Actor(TENANT_ID, ACTOR_ID, "corr-1");
    }

    private ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setVersion(3);
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S4");
        project.setAssignmentStatus("ASSIGNED");
        return project;
    }

    private ProjectStageInstanceDO stage(Long id, String code, int sort, String status, int version) {
        ProjectStageInstanceDO stage = new ProjectStageInstanceDO();
        stage.setId(id);
        stage.setTenantId(TENANT_ID);
        stage.setProjectId(PROJECT_ID);
        stage.setStageCode(code);
        stage.setSortOrder(sort);
        stage.setStatus(status);
        stage.setVersion(version);
        return stage;
    }
}
