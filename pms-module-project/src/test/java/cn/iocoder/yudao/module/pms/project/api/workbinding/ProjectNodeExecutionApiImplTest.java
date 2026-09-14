package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskExecutionRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ProjectNodeExecutionApiImplTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectTaskExecutionMapper mapper = mock(ProjectTaskExecutionMapper.class);
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper nodes =
            mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.class);
    private final ProjectNodeExecutionApiImpl api = new ProjectNodeExecutionApiImpl(projects, mapper, nodes);
    private final ProjectTaskExecutionQuery query = new ProjectTaskExecutionQuery(100L, 200L, 300L);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(0L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"IN_PROGRESS", "PENDING_ACCEPT"})
    void activeCustomStageExecutionIsWritableWithoutSingleStageOrLegacyAssetIdentity(String taskStatus) {
        when(mapper.selectCurrent(any())).thenReturn(row("ACTIVE", "ACTIVE", "ACTIVE", taskStatus, 500L, 1));
        var observed = api.inspect(query);
        assertTrue(observed.writable());
        assertEquals(400L, observed.planVersionId());
        assertEquals(500L, observed.executionId());
        assertEquals(java.time.LocalDateTime.of(2026,9,14,9,0), observed.startedAt());
        verify(mapper).selectCurrent(argThat(q -> q.tenantId()==0L && q.projectId()==100L
                && q.taskId()==200L && q.executionContractId()==300L));
    }

    @ParameterizedTest
    @CsvSource({"NORMAL_CLOSED,ACTIVE,ACTIVE,IN_PROGRESS", "ACTIVE,PENDING,ACTIVE,IN_PROGRESS",
            "ACTIVE,DONE,ACTIVE,IN_PROGRESS", "ACTIVE,ACTIVE,PENDING,IN_PROGRESS",
            "ACTIVE,ACTIVE,DONE,DONE", "ACTIVE,ACTIVE,TERMINATED,CLOSED",
            "ACTIVE,ACTIVE,ACTIVE,PENDING_ASSIGN", "ACTIVE,ACTIVE,ACTIVE,PAUSED"})
    void unavailableStatesRemainReadableButNeverGrantWrite(String project, String stage, String round, String task) {
        when(mapper.selectCurrent(any())).thenReturn(row(project, stage, round, task, 500L, 1));
        var observed = api.inspect(query);
        assertFalse(observed.writable());
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(observed));
        verifyNoInteractions(projects);
    }

    @Test void revalidationRejectsNewRoundAndVersionChangesAndUsesProjectFirstLock() {
        when(mapper.selectCurrent(any())).thenReturn(row("ACTIVE", "ACTIVE", "ACTIVE", "IN_PROGRESS", 500L, 1));
        var observed = api.inspect(query);
        var project = new ProjectMasterDO(); project.setId(100L); project.setTenantId(0L);
        when(projects.selectByIdForUpdate(100L)).thenReturn(project);
        when(mapper.selectCurrentForUpdate(any())).thenReturn(row("ACTIVE", "ACTIVE", "ACTIVE", "IN_PROGRESS", 500L, 1));
        assertEquals(observed, api.lockAndRevalidate(observed));
        var order = inOrder(projects, mapper);
        order.verify(projects).selectByIdForUpdate(100L);
        order.verify(mapper).selectCurrentForUpdate(any());
        when(mapper.selectCurrentForUpdate(any())).thenReturn(row("ACTIVE", "ACTIVE", "ACTIVE", "IN_PROGRESS", 501L, 1));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(observed));
        when(mapper.selectCurrentForUpdate(any())).thenReturn(row("ACTIVE", "ACTIVE", "ACTIVE", "IN_PROGRESS", 500L, 2));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidate(observed));
    }

    @Test void missingContextOrTenantFailsClosed() {
        assertThrows(ServiceException.class, () -> api.inspect(query));
        TenantContextHolder.clear();
        clearInvocations(mapper);
        assertThrows(ServiceException.class, () -> api.inspect(query));
        verifyNoInteractions(mapper);
    }

    private ProjectTaskExecutionRecord row(String project, String stage, String round, String task, long execution, int version) {
        return new ProjectTaskExecutionRecord(100L, 1, project, 200L, 2, task, 300L, 1, 400L,
                execution, version, 1, round, 600L, 1, stage, stage, java.time.LocalDateTime.of(2026,9,14,9,0));
    }
}
