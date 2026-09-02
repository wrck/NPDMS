package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectAcceptanceStageFactQuery;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectStageEntrySnapshotQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAcceptanceStageFactApiImplTest {

    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectStageInstanceMapper stageMapper;
    @Mock private ProjectStageSnapshotMapper snapshotMapper;
    private ProjectAcceptanceStageFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        api = new ProjectAcceptanceStageFactApiImpl(projectMapper, stageMapper, snapshotMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsConfiguredAcceptanceStageWithoutSnapshotBeforeEntry() {
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(project(3, "ACTIVE", "S4"));
        when(stageMapper.selectByProjectIdAndStageCode(10L, "S5")).thenReturn(stage("S5", "PENDING"));

        var fact = api.lockAndRead(new ProjectAcceptanceStageFactQuery(1L, 10L, 3, "op-1"));

        assertEquals(ProjectFactOutcome.FOUND, fact.outcome());
        assertEquals("S4", fact.currentStageCode());
        assertEquals("S5", fact.acceptanceStageCode());
        assertNull(fact.projectStageSnapshotId());
    }

    @Test
    void returnsImmutableEntrySnapshotWhenAlreadyInAcceptanceStage() {
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(project(3, "ACTIVE", "S5"));
        when(stageMapper.selectByProjectIdAndStageCode(10L, "S5")).thenReturn(stage("S5", "ACTIVE"));
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setId(99L);
        snapshot.setTenantId(1L);
        snapshot.setProjectId(10L);
        snapshot.setStageCode("S5");
        snapshot.setOperationType("STAGE_ENTRY");
        snapshot.setAfterStage("S5");
        when(snapshotMapper.selectLatestStageEntry(new ProjectStageEntrySnapshotQuery(1L, 10L, "S5")))
                .thenReturn(snapshot);

        var fact = api.lockAndRead(new ProjectAcceptanceStageFactQuery(1L, 10L, 3, "op-2"));

        assertEquals(ProjectFactOutcome.FOUND, fact.outcome());
        assertEquals(99L, fact.projectStageSnapshotId());
    }

    @Test
    void returnsVersionConflictBeforeReadingStageFacts() {
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(project(4, "ACTIVE", "S4"));

        var fact = api.lockAndRead(new ProjectAcceptanceStageFactQuery(1L, 10L, 3, "op-3"));

        assertEquals(ProjectFactOutcome.VERSION_CONFLICT, fact.outcome());
    }

    @Test
    void lockedReadRequiresExistingTransaction() throws Exception {
        Transactional transactional = ProjectAcceptanceStageFactApiImpl.class
                .getMethod("lockAndRead", ProjectAcceptanceStageFactQuery.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    private ProjectMasterDO project(Integer version, String lifecycleStatus, String currentStage) {
        ProjectMasterDO row = new ProjectMasterDO();
        row.setId(10L);
        row.setTenantId(1L);
        row.setVersion(version);
        row.setLifecycleStatus(lifecycleStatus);
        row.setCurrentStage(currentStage);
        return row;
    }

    private ProjectStageInstanceDO stage(String code, String status) {
        ProjectStageInstanceDO row = new ProjectStageInstanceDO();
        row.setId(20L);
        row.setTenantId(1L);
        row.setProjectId(10L);
        row.setStageCode(code);
        row.setStatus(status);
        return row;
    }
}
