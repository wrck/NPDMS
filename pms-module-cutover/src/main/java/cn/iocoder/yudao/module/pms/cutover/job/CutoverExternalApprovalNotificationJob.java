package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external.CutoverExternalApprovalNotificationService;

import java.time.LocalDateTime;

public class CutoverExternalApprovalNotificationJob implements JobHandler {

    static final int BATCH_SIZE = 50;

    private final CutoverExternalApprovalNotificationService notificationService;

    public CutoverExternalApprovalNotificationJob(CutoverExternalApprovalNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    @TenantJob
    public String execute(String param) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        var result = notificationService.deliverDue(tenantId, LocalDateTime.now(), BATCH_SIZE);
        return "割接外部审批提醒投递完成：受理 " + result.accepted() + " 条，结果未知 "
                + result.deliveryUnknown() + " 条，待重试 " + result.retryScheduled() + " 条";
    }
}
