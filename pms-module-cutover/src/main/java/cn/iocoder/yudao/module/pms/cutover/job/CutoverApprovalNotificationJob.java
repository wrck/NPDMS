package cn.iocoder.yudao.module.pms.cutover.job;

import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CutoverApprovalNotificationJob implements JobHandler {

    static final int BATCH_SIZE = 50;

    private final CutoverApprovalNotificationService notificationService;

    @Override
    @TenantJob
    public String execute(String param) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        var result = notificationService.deliverDue(tenantId, LocalDateTime.now(), BATCH_SIZE);
        return "割接审批通知投递成功 " + result.sent() + " 条，待重试 " + result.retried() + " 条";
    }
}
