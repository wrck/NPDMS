package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ProjectServiceManagerAssignedPayload;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 消费服务经理指派Outbox并投递站内信。 */
@Component
@Slf4j
public class ProjectServiceManagerNotificationJob implements JobHandler {

    static final int BATCH_SIZE = 50;
    static final long MAX_RETRY_DELAY_MINUTES = 60;

    @Resource
    private PlatformOutboxDeliveryApi outboxDeliveryApi;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @Override
    @TenantJob
    public String execute(String param) {
        LocalDateTime dueAt = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(dueAt, BATCH_SIZE));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                deliver(message);
                outboxDeliveryApi.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } catch (RuntimeException ex) {
                LocalDateTime nextRetryTime = dueAt.plusMinutes(retryDelayMinutes(message.retryCount()));
                outboxDeliveryApi.scheduleRetry(message.eventId(), message.retryCount(), nextRetryTime);
                retried++;
                log.warn("[execute][服务经理通知事件({})投递失败，计划于({})重试]",
                        message.eventId(), nextRetryTime, ex);
            }
        }
        return String.format("服务经理通知投递成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private void deliver(PlatformOutboxMessageDTO message) {
        if (message == null || message.eventId() == null || message.eventId().isBlank()
                || !"ProjectServiceManagerAssigned".equals(message.eventType())
                || !Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("服务经理通知事件元数据不完整");
        }
        ProjectServiceManagerAssignedPayload payload = JsonUtils.parseObject(
                message.payload(), ProjectServiceManagerAssignedPayload.class);
        validatePayload(payload);
        NotifySendSingleToUserReqDTO request = new NotifySendSingleToUserReqDTO();
        request.setUserId(payload.recipientUserId());
        request.setTemplateCode(payload.templateCode());
        request.setTemplateParams(payload.templateParamsSnapshot());
        request.setDeliveryKey(message.eventId());
        notifyMessageSendApi.sendSingleMessageToAdmin(request);
    }

    private static void validatePayload(ProjectServiceManagerAssignedPayload payload) {
        if (payload == null || payload.assignmentId() == null || payload.projectId() == null
                || payload.recipientUserId() == null || payload.templateCode() == null
                || payload.templateCode().isBlank() || payload.templateParamsSnapshot() == null
                || payload.assignmentType() == null || payload.levelCode() == null
                || payload.effectiveFrom() == null) {
            throw new IllegalArgumentException("服务经理通知冻结载荷不完整");
        }
    }

    private static long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, MAX_RETRY_DELAY_MINUTES);
    }
}
