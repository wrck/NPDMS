package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CutoverApprovalNotificationJobTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void delegatesCurrentTenantBatchWithoutActivatingScheduler() {
        CutoverApprovalNotificationService service = mock(CutoverApprovalNotificationService.class);
        TenantContextHolder.setTenantId(9L);
        when(service.deliverDue(eq(9L), any(), eq(50))).thenReturn(
                new CutoverApprovalNotificationService.DeliveryResult(2, 1));
        CutoverApprovalNotificationJob job = new CutoverApprovalNotificationJob(service);

        String result = job.execute(null);

        assertThat(result).isEqualTo("割接审批通知投递成功 2 条，待重试 1 条");
        verify(service).deliverDue(eq(9L), any(), eq(50));
    }
}
