package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectStageExecutionRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskExecutionMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageExecutionApiTest {
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectTaskExecutionMapper tasks = mock(ProjectTaskExecutionMapper.class);
    final ProjectNodeExecutionMapper stages = mock(ProjectNodeExecutionMapper.class);
    final ProjectNodeExecutionApi api = new ProjectNodeExecutionApiImpl(projects, tasks, stages);
    final ProjectStageExecutionQuery query = new ProjectStageExecutionQuery(9L, 90L, 99L);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void customStageWithoutAnyTaskReceivesItsOwnCurrentExecution() {
        when(stages.selectCurrentStageContext(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",101L,1));
        var result = api.inspectStage(query);
        assertTrue(result.writable()); assertEquals(2, result.roundNo());
        assertEquals(100L, result.planVersionId()); assertEquals(101L, result.executionId());
        assertEquals(query, result.query());
        verify(stages).selectCurrentStageContext(argThat(q -> q.tenantId()==1L && q.projectId()==9L
                && q.stageId()==90L && q.executionContractId()==99L));
        verifyNoInteractions(tasks, projects);
    }

    @ParameterizedTest
    @CsvSource({"CLOSED_NORMAL,ACTIVE,ACTIVE", "ACTIVE,PENDING,ACTIVE", "ACTIVE,DONE,ACTIVE",
            "ACTIVE,ACTIVE,PENDING", "ACTIVE,ACTIVE,DONE", "ACTIVE,ACTIVE,TERMINATED"})
    void unavailableStagesAreReadOnly(String project, String stage, String execution) {
        when(stages.selectCurrentStageContext(any())).thenReturn(row(project,stage,execution,101L,1));
        var observed = api.inspectStage(query);
        assertFalse(observed.writable());
        assertThrows(ServiceException.class, () -> api.lockAndRevalidateStage(observed));
        verifyNoInteractions(projects, tasks);
        verify(stages, never()).selectCurrentStageContextForUpdate(any());
    }

    @Test void revalidationRejectsReworkAndVersionChangesUnderProjectFirstLock() {
        when(stages.selectCurrentStageContext(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",101L,1));
        var expected = api.inspectStage(query);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        when(stages.selectCurrentStageContextForUpdate(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",101L,1));
        assertEquals(expected, api.lockAndRevalidateStage(expected));
        var order = inOrder(projects, stages);
        order.verify(projects).selectByIdForUpdate(9L);
        order.verify(stages).selectCurrentStageContextForUpdate(any());
        when(stages.selectCurrentStageContextForUpdate(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",102L,1));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidateStage(expected));
        when(stages.selectCurrentStageContextForUpdate(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",101L,2));
        assertThrows(ServiceException.class, () -> api.lockAndRevalidateStage(expected));
        when(stages.selectCurrentStageContextForUpdate(any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> api.lockAndRevalidateStage(expected));
    }

    @Test void missingOrInvalidIdentitiesCannotResolveAnArbitraryStage() {
        assertThrows(ServiceException.class, () -> api.inspectStage(query));
        clearInvocations(stages);
        assertThrows(ServiceException.class, () -> api.inspectStage(null));
        assertThrows(ServiceException.class, () -> api.inspectStage(new ProjectStageExecutionQuery(9L, null, 99L)));
        assertThrows(ServiceException.class, () -> api.inspectStage(new ProjectStageExecutionQuery(9L, 90L, 0L)));
        TenantContextHolder.clear();
        assertThrows(ServiceException.class, () -> api.inspectStage(query));
        verifyNoInteractions(stages, tasks, projects);
    }

    @Test void changedTenantCannotUsePreviouslyObservedExecution() {
        when(stages.selectCurrentStageContext(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",101L,1));
        var expected = api.inspectStage(query);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> api.lockAndRevalidateStage(expected));
        verify(stages, never()).selectCurrentStageContextForUpdate(any());
    }

    @Test void firstOwnerWriteMarksStartedAndReturnsTheRefreshedExecutionVersion() {
        var expected = prepareHandling();
        when(stages.beginStageHandlingIfCurrent(any())).thenReturn(1);
        when(stages.selectCurrentStageContext(any())).thenReturn(row("ACTIVE","ACTIVE","ACTIVE",101L,2));
        assertEquals(2, api.beginStageHandling(expected, 9L).executionVersion());
        verify(stages).beginStageHandlingIfCurrent(argThat(command -> command.tenantId()==1L
                && command.projectId()==9L && command.executionId()==101L && command.planVersionId()==100L
                && command.contractId()==99L && command.expectedVersion()==1 && command.actorId()==9L
                && command.occurredAt()!=null));
        verifyNoInteractions(tasks);
    }

    @Test void laterOwnerWritesDoNotOverwriteTheOriginalStartEvidence() {
        var expected = prepareHandling();
        var execution = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO();
        execution.setStartedAt(java.time.LocalDateTime.of(2026,9,14,10,0));
        when(stages.selectById(101L)).thenReturn(execution);
        assertEquals(expected, api.beginStageHandling(expected, 9L));
        verify(stages,never()).beginStageHandlingIfCurrent(any());
    }

    @Test void failedStartCasDoesNotReportSuccessfulHandling() {
        var expected = prepareHandling();
        assertThrows(ServiceException.class, () -> api.beginStageHandling(expected, 9L));
        assertThrows(ServiceException.class, () -> api.beginStageHandling(expected, 0L));
        verify(stages,times(1)).beginStageHandlingIfCurrent(any());
    }

    private cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext prepareHandling() {
        var record = row("ACTIVE","ACTIVE","ACTIVE",101L,1);
        when(stages.selectCurrentStageContext(any())).thenReturn(record);
        when(stages.selectCurrentStageContextForUpdate(any())).thenReturn(record);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(1L);
        when(projects.selectByIdForUpdate(9L)).thenReturn(project);
        when(stages.selectById(101L)).thenReturn(new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO());
        return api.inspectStage(query);
    }

    private ProjectStageExecutionRecord row(String project, String stage, String execution, Long id, Integer version) {
        return new ProjectStageExecutionRecord(9L,1,project,90L,1,stage,99L,1,100L,id,version,2,execution);
    }
}
