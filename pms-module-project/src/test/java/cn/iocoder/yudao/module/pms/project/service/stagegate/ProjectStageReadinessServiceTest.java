package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectStageReadinessServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 9L;
    private static final Long ACTOR_ID = 11L;

    private ProjectStageGateProviderRegistry providerRegistry;
    private ProjectStageReadinessService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        ProjectStageInstanceMapper stageMapper = mock(ProjectStageInstanceMapper.class);
        ProjectGateInstanceMapper gateMapper = mock(ProjectGateInstanceMapper.class);
        ProjectGateReferenceInstanceMapper referenceMapper = mock(ProjectGateReferenceInstanceMapper.class);
        providerRegistry = mock(ProjectStageGateProviderRegistry.class);
        ProjectScopeApi scopeApi = mock(ProjectScopeApi.class);
        ProjectParticipantFactApi participantFactApi = mock(ProjectParticipantFactApi.class);
        PermissionApi permissionApi = mock(PermissionApi.class);
        service = new ProjectStageReadinessService(projectMapper, stageMapper, gateMapper, referenceMapper,
                providerRegistry, scopeApi, participantFactApi, permissionApi);

        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S0");
        project.setVersion(4);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        when(stageMapper.selectStagePair(any())).thenReturn(List.of(
                new ProjectStageInstanceDO().setId(21L).setStageCode("S0").setStatus("ACTIVE"),
                new ProjectStageInstanceDO().setId(22L).setStageCode("S1").setStatus("PENDING")));
        ProjectGateInstanceDO gate = new ProjectGateInstanceDO().setId(31L).setGateCode("G-S0-EXIT")
                .setGateType("EXIT").setStageCode("S0").setStatus("PENDING").setVersion(0);
        when(gateMapper.selectExitGates(any())).thenReturn(List.of(gate));
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

        assertEquals(List.of("START_PROCESS"),
                notStarted.gates().getFirst().references().getFirst().allowedActions());
    }

    @Test
    void rejectsAStagePairThatSkipsTheFrozenAdjacentStage() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S0");

        assertThrows(RuntimeException.class, () -> ProjectStageReadinessService.requirePair(project, List.of(
                new ProjectStageInstanceDO().setStageCode("S0").setStatus("ACTIVE"),
                new ProjectStageInstanceDO().setStageCode("S2").setStatus("PENDING"))));
    }

    private static ProjectStageGateFact processFact(String unmetCode) {
        return new ProjectStageGateFact(ProjectStageGateFactProviderApi.PROVIDER_BPM_PROCESS, "PROCESS",
                "pi-1", "def-1", "1", ProjectStageGateOutcome.UNSATISFIED, unmetCode);
    }
}
