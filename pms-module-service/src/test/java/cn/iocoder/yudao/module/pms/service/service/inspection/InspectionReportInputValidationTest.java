package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import java.util.List;

class InspectionReportInputValidationTest {
    @Test void nativeReportCannotAttachForeignTenantTaskOrAttemptResults() {
        var access=mock(InspectionAccessService.class);var reports=mock(InspectionReportRevisionMapper.class);
        var links=mock(InspectionReportResultMapper.class);var results=mock(InspectionDetectionResultMapper.class);
        var snapshots=mock(InspectionTaskRuleSnapshotMapper.class);var attempts=mock(InspectionAttemptMapper.class);
        var task=new InspectionTaskDO();task.setId(10L);task.setTenantId(7L);task.setCurrentAttemptId(30L);task.setStatusCode("REPORT_PENDING");
        var attempt=new InspectionAttemptDO();attempt.setId(30L);attempt.setTaskId(10L);attempt.setTenantId(7L);attempt.setStatusCode("COMPLETED");attempt.setProviderCode("PLATFORM");
        var snapshot=new InspectionTaskRuleSnapshotDO();snapshot.setId(500L);snapshot.setAttemptId(30L);snapshot.setDeviceId(50L);snapshot.setRuleRevisionId(600L);
        var result=new InspectionDetectionResultDO();result.setId(1000L);result.setTenantId(8L);result.setTaskId(11L);result.setAttemptId(31L);
        result.setDeviceId(50L);result.setExecutionId(40L);result.setRuleSnapshotId(500L);result.setRuleRevisionId(600L);result.setSourceCode("PLATFORM");result.setOutcome("PASS");
        when(access.task(any())).thenReturn(task);when(attempts.selectById(30L)).thenReturn(attempt);
        when(results.selectCurrent(any())).thenReturn(List.of(result));when(snapshots.selectForTask(any())).thenReturn(List.of(snapshot));
        when(reports.selectForTask(any())).thenReturn(List.of());
        doAnswer(inv->{inv.<InspectionReportRevisionDO>getArgument(0).setId(70L);return 1;}).when(reports).insert(any(InspectionReportRevisionDO.class));
        var service=new InspectionReportService(null,access,mock(InspectionTaskService.class),reports,links,null,results,snapshots,
                mock(InspectionTaskDeviceMapper.class),mock(InspectionExecutionMapper.class),attempts,null,null,null,null,null);
        assertThrows(InspectionException.class,()->service.generate(new InspectionReportService.Generate(new InspectionRequestContext(7L,9L,"test","test",1L),10L,null)));
        verify(links,never()).insert(any(InspectionReportResultDO.class));
    }
}
