package cn.iocoder.yudao.module.pms.acceptance.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.service.satisfaction.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SatisfactionTaskBusinessObjectProviderTest {
    private final SatisfactionCollectionTaskMapper tasks = mock(SatisfactionCollectionTaskMapper.class);
    private final SatisfactionResultMapper results = mock(SatisfactionResultMapper.class);
    private final SatisfactionResultFileMapper files = mock(SatisfactionResultFileMapper.class);
    private final SatisfactionBusinessResultSource source = mock(SatisfactionBusinessResultSource.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final SatisfactionTaskBusinessObjectProvider provider = new SatisfactionTaskBusinessObjectProvider(tasks,
            results, files, mock(SatisfactionTaskManagementService.class), source, executions,
            mock(ProjectScopeApi.class), mock(PermissionApi.class));
    private final SatisfactionCollectionTaskDO task = new SatisfactionCollectionTaskDO();
    private final SatisfactionResultDO result = new SatisfactionResultDO();
    private final ProjectTaskExecutionContext execution = new ProjectTaskExecutionContext(9L, 1L, 2L, 1,
            3L, 1, 4L, 5L, 1, 1, 6L, 1, true, LocalDateTime.now());

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(executions.lockAndRevalidate(execution)).thenReturn(execution);
        task.setId(10L); task.setTenantId(7L); task.setProjectId(9L); task.setResultId(20L); task.setVersion(1L);
        result.setId(20L); result.setTenantId(7L); result.setCollectionTaskId(10L); result.setPassed(true);
        result.setResultStatus("EFFECTIVE"); result.setArchiveStatus("ARCHIVED");
        when(tasks.selectByIdForUpdate(7L, 10L)).thenReturn(task);
        when(results.selectByIdForUpdate(7L, 20L)).thenReturn(result);
        when(files.selectListByResult(any())).thenReturn(List.of(file("SIGNATURE"), file("RESULT_DOCUMENT")));
        when(source.lockAndInspect(any())).thenReturn(BusinessResultSource.Observation.available(
                new BusinessResultSource.Result(7L, 9L, SatisfactionBusinessResultSource.TYPE, "10", "20", "1", "1",
                        BusinessResultSource.Validity.CURRENT, LocalDateTime.now())));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void signedPassedArchivedResultCompletes() { assertTrue(complete()); }
    @Test void signedButFailedDoesNotComplete() {
        result.setPassed(false); result.setResultStatus("FAILED");
        assertFalse(complete()); verifyNoInteractions(source);
    }
    @Test void pendingArchiveDoesNotComplete() {
        result.setArchiveStatus("PENDING_COMPENSATION");
        assertFalse(complete()); verifyNoInteractions(source);
    }
    @Test void missingSignatureDoesNotComplete() {
        when(files.selectListByResult(any())).thenReturn(List.of(file("RESULT_DOCUMENT")));
        assertFalse(complete()); verifyNoInteractions(source);
    }
    @Test void revokedResultAndUnavailableFileDoNotComplete() {
        result.setEffectiveTo(LocalDateTime.now()); assertFalse(complete());
        result.setEffectiveTo(null);
        when(source.lockAndInspect(any())).thenReturn(BusinessResultSource.Observation.absent(
                BusinessResultSource.Status.UNAVAILABLE, "file unavailable"));
        assertFalse(complete());
    }
    @Test void rejectsCrossProjectAndCrossTenantOwners() {
        task.setProjectId(99L); assertThrows(IllegalStateException.class, this::complete);
        task.setProjectId(9L); task.setTenantId(8L); assertThrows(IllegalStateException.class, this::complete);
        verifyNoInteractions(source);
    }
    @Test void rejectsStaleExecutionBeforeInspectingOwnerEvidence() {
        when(executions.lockAndRevalidate(execution)).thenThrow(new IllegalStateException("stale execution"));
        assertThrows(IllegalStateException.class, this::complete);
        verifyNoInteractions(tasks, results, files, source);
    }
    private boolean complete() {
        return provider.lockCompletionFact(new CompletionContext(7L, execution), "10").completionFacts()
                .get(SatisfactionTaskBusinessObjectProvider.COMPLETE);
    }
    private SatisfactionResultFileDO file(String role) {
        var file = new SatisfactionResultFileDO(); file.setTenantId(7L); file.setResultId(20L); file.setFileRole(role); return file;
    }
}
