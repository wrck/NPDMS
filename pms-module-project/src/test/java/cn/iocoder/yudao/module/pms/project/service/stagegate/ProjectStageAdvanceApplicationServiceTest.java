package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
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
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectStageAdvanceApplicationServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 9L;
    private static final Long ACTOR_ID = 11L;

    private PlatformCommandExecutionApi commandExecutionApi;
    private ProjectStageGateProviderRegistry providerRegistry;
    private ProjectMasterMapper projectMapper;
    private ProjectStageInstanceMapper stageMapper;
    private ProjectGateInstanceMapper gateMapper;
    private ProjectGateReferenceInstanceMapper referenceMapper;
    private ProjectStageSnapshotMapper snapshotMapper;
    private ProjectStageSnapshotRepository snapshotRepository;
    private ProjectStageAdvanceApplicationService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        commandExecutionApi = mock(PlatformCommandExecutionApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        ProjectScopeApi projectScopeApi = mock(ProjectScopeApi.class);
        providerRegistry = mock(ProjectStageGateProviderRegistry.class);
        projectMapper = mock(ProjectMasterMapper.class);
        stageMapper = mock(ProjectStageInstanceMapper.class);
        gateMapper = mock(ProjectGateInstanceMapper.class);
        referenceMapper = mock(ProjectGateReferenceInstanceMapper.class);
        snapshotMapper = mock(ProjectStageSnapshotMapper.class);
        snapshotRepository = mock(ProjectStageSnapshotRepository.class);
        service = new ProjectStageAdvanceApplicationService(commandExecutionApi, permissionApi, projectScopeApi,
                mock(ProjectParticipantFactApi.class), mock(ProjectStageGateProcessOwnerApi.class), providerRegistry,
                projectMapper, stageMapper, gateMapper, referenceMapper, mock(ProjectMemberAssignmentMapper.class),
                snapshotMapper, snapshotRepository);

        when(permissionApi.hasAnyPermissions(ACTOR_ID, "pms:project:update")).thenReturn(true);
        ProjectScopeResult scope = new ProjectScopeResult(PROJECT_ID, 3L, Set.of(PROJECT_ID), Set.of());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope);
        doAnswer(invocation -> {
            Supplier<ProjectStageAdvanceResult> operation = invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, operation.get());
        }).when(commandExecutionApi).execute(any(), anyString(), eq(ProjectStageAdvanceResult.class), any(), any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void advancesAdjacentStageAfterOwnerFactsAreSatisfied() {
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
                .setStageCode("S1").setSortOrder(1).setStatus("PENDING").setVersion(0);
        when(stageMapper.selectStagePairForUpdate(any())).thenReturn(List.of(current, next));

        ProjectGateInstanceDO gate = new ProjectGateInstanceDO().setId(31L).setProjectId(PROJECT_ID)
                .setGateCode("G-S0-EXIT").setGateType("EXIT").setStageCode("S0")
                .setStatus("PENDING").setVersion(0);
        ProjectGateReferenceInstanceDO reference = new ProjectGateReferenceInstanceDO().setId(41L).setGateId(31L)
                .setRefType("TASK").setRefCode("T-S0").setVersion(0);
        when(gateMapper.selectExitGatesForUpdate(any())).thenReturn(List.of(gate));
        when(referenceMapper.selectOrderedForUpdate(any())).thenReturn(List.of(reference));
        when(providerRegistry.lockAndRevalidate(eq(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK), any()))
                .thenReturn(new ProjectStageGateFact(ProjectStageGateFactProviderApi.PROVIDER_PROJ_TASK, "TASK",
                        "51", "DONE", "2", ProjectStageGateOutcome.SATISFIED, null));
        when(gateMapper.updateStatusIfMatch(any())).thenReturn(1);
        when(stageMapper.updateStatusIfMatch(any())).thenReturn(1);
        when(projectMapper.advanceStageIfMatch(any())).thenReturn(1);
        when(snapshotMapper.selectNextSnapshotNo(any())).thenReturn(1);
        when(snapshotRepository.append(any())).thenAnswer(invocation -> {
            ProjectStageSnapshotDO snapshot = invocation.getArgument(0);
            snapshot.setId(61L);
            return 1;
        });

        ProjectStageAdvanceResult result = service.advance(new ProjectStageAdvanceCommand(PROJECT_ID, 4, "S0", 3L,
                "advance-1", "a".repeat(64)), new ProjectStageAdvanceApplicationService.Actor(TENANT_ID, ACTOR_ID, "corr-1"));

        assertEquals("S0", result.beforeStage());
        assertEquals("S1", result.afterStage());
        assertEquals(5, result.projectVersion());
        assertEquals(61L, result.stageSnapshotId());
        assertFalse(result.replayed());
        ArgumentCaptor<ProjectStageAdvanceUpdate> update = ArgumentCaptor.forClass(ProjectStageAdvanceUpdate.class);
        verify(projectMapper).advanceStageIfMatch(update.capture());
        assertEquals("S0", update.getValue().expectedCurrentStage());
        assertEquals("S1", update.getValue().targetStage());
    }
}
