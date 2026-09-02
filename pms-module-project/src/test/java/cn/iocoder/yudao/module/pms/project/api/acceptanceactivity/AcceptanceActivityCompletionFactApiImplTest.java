package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceActivityCompletionFactApiImplTest {

    @Mock AcceptanceActivityMapper activityMapper;
    @Mock AcceptanceReportVersionMapper reportMapper;
    @Mock AcceptanceReportAttachmentMapper attachmentMapper;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void completesOnlyAnExactActivityWithACompleteCurrentReport() {
        AcceptanceActivityDO activity = activity();
        AcceptanceReportVersionDO report = report();
        when(activityMapper.selectByIdForUpdate(any())).thenReturn(activity);
        when(reportMapper.selectByIdForUpdate(any())).thenReturn(report);
        when(attachmentMapper.selectByReportVersion(41L)).thenReturn(List.of(new AcceptanceReportAttachmentDO()));
        when(activityMapper.completeIfPending(any())).thenReturn(1);
        var api = new AcceptanceActivityCompletionFactApiImpl(activityMapper, reportMapper, attachmentMapper);

        var result = api.lockAndComplete(command(0, 2));

        assertEquals("COMPLETED", result.outcome());
        assertEquals(1, result.activityVersion());
        assertEquals(41L, result.reportVersionId());
        verify(activityMapper).completeIfPending(any());
    }

    @Test
    void incompleteReportLeavesActivityUntouched() {
        AcceptanceActivityDO activity = activity();
        AcceptanceReportVersionDO report = report();
        report.setAcceptorName(null);
        when(activityMapper.selectByIdForUpdate(any())).thenReturn(activity);
        when(reportMapper.selectByIdForUpdate(any())).thenReturn(report);
        var api = new AcceptanceActivityCompletionFactApiImpl(activityMapper, reportMapper, attachmentMapper);

        var result = api.lockAndComplete(command(0, 2));

        assertEquals("REPORT_INCOMPLETE", result.outcome());
        verify(activityMapper, never()).completeIfPending(any());
        verify(attachmentMapper, never()).selectByReportVersion(any());
    }

    private AcceptanceActivityCompletionCommand command(int activityVersion, int reportVersion) {
        return new AcceptanceActivityCompletionCommand(7L, 11L, 21L, 31L, 51L,
                activityVersion, reportVersion, "operation-1");
    }

    private AcceptanceActivityDO activity() {
        AcceptanceActivityDO row = new AcceptanceActivityDO();
        row.setId(51L);
        row.setTenantId(7L);
        row.setProjectId(11L);
        row.setProjectTaskId(21L);
        row.setExecutionContractId(31L);
        row.setActivityStatus("PENDING");
        row.setCurrentReportVersionId(41L);
        row.setVersion(0);
        return row;
    }

    private AcceptanceReportVersionDO report() {
        AcceptanceReportVersionDO row = new AcceptanceReportVersionDO();
        row.setId(41L);
        row.setTenantId(7L);
        row.setAcceptanceId(51L);
        row.setReportVersionNo(2);
        row.setReportStatus("EFFECTIVE");
        row.setAcceptanceTime(LocalDateTime.now());
        row.setConclusionCode("PASS");
        row.setAcceptorName("验收人");
        return row;
    }
}
