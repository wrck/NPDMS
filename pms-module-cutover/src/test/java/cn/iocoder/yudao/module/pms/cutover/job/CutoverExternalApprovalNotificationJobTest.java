package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CutoverExternalApprovalNotificationJobTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void delegatesCurrentTenantBatchAndReportsControlledResults() {
        CutoverExternalApprovalNotificationService service = mock(CutoverExternalApprovalNotificationService.class);
        when(service.deliverDue(eq(1L), isA(java.time.LocalDateTime.class),
                eq(CutoverExternalApprovalNotificationJob.BATCH_SIZE)))
                .thenReturn(new CutoverExternalApprovalNotificationService.DeliveryResult(2, 1, 3));
        TenantContextHolder.setTenantId(1L);

        String output = new CutoverExternalApprovalNotificationJob(service).execute("");

        assertThat(output).isEqualTo("割接外部审批提醒投递完成：受理 2 条，结果未知 1 条，待重试 3 条");
    }
}
