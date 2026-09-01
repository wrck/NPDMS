package cn.iocoder.yudao.module.pms.cutover.service.approval.notification;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationClaimQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CutoverApprovalNotificationService {

    private static final String PROVIDER_FAILURE = "NOTIFY_PROVIDER_FAILURE";
    private static final int MAX_BATCH_SIZE = 100;

    private final CutoverApprovalNotificationMapper notificationMapper;
    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverApprovalNodeMapper nodeMapper;
    private final CutoverTaskMapper taskMapper;
    private final CutoverApprovalNotificationProviderExecutor providerExecutor;

    @Transactional
    public DeliveryResult deliverDue(long tenantId, LocalDateTime dueAt, int batchSize) {
        require(tenantId > 0 && dueAt != null && batchSize > 0 && batchSize <= MAX_BATCH_SIZE,
                "通知领取参数无效");
        require(Objects.equals(TenantContextHolder.getRequiredTenantId(), tenantId), "通知租户上下文不一致");
        int sent = 0;
        int retried = 0;
        var rows = notificationMapper.selectDueForUpdateSkipLocked(
                new ApprovalNotificationClaimQuery(tenantId, dueAt, batchSize));
        for (CutoverApprovalNotificationDO row : rows) {
            validateRow(tenantId, row);
            try {
                long messageId = send(row);
                update(row, "SENT", messageId, row.getRetryCount(), null, null, dueAt, dueAt);
                sent++;
            } catch (NotificationProviderFailure failure) {
                int nextRetryCount = row.getRetryCount() + 1;
                update(row, "PENDING_RETRY", null, nextRetryCount,
                        dueAt.plusMinutes(retryDelayMinutes(row.getRetryCount())), PROVIDER_FAILURE, null, dueAt);
                retried++;
            }
        }
        return new DeliveryResult(sent, retried);
    }

    private long send(CutoverApprovalNotificationDO notification) {
        CutoverApprovalInstanceDO root = instanceMapper.selectById(notification.getApprovalInstanceId());
        CutoverApprovalNodeDO node = nodeMapper.selectById(notification.getApprovalNodeId());
        require(root != null && node != null
                        && Objects.equals(root.getTenantId(), notification.getTenantId())
                        && Objects.equals(node.getTenantId(), notification.getTenantId())
                        && Objects.equals(node.getApprovalInstanceId(), root.getId()),
                "通知审批身份损坏");
        CutoverTaskDO task = taskMapper.selectById(root.getTaskId());
        require(task != null && Objects.equals(task.getTenantId(), notification.getTenantId())
                        && task.getTaskNo() != null && task.getTaskName() != null,
                "通知任务身份损坏");
        NotifySendSingleToUserReqDTO request = new NotifySendSingleToUserReqDTO();
        request.setUserId(notification.getRecipientUserId());
        request.setTemplateCode(notification.getTemplateCode());
        request.setDeliveryKey(notification.getDeliveryKey());
        request.setTemplateParams(Map.of(
                "taskId", task.getId(),
                "taskCode", task.getTaskNo(),
                "taskName", task.getTaskName(),
                "approvalInstanceId", root.getId(),
                "nodeNo", node.getNodeNo(),
                "nodeCode", node.getNodeCode(),
                "link", "/pms/cutover/cutover-task?taskId=" + task.getId()));
        try {
            Long messageId = providerExecutor.send(request);
            if (messageId == null || messageId <= 0) throw new IllegalStateException("站内信消息编号无效");
            return messageId;
        } catch (RuntimeException ex) {
            throw new NotificationProviderFailure(ex);
        }
    }

    private void update(CutoverApprovalNotificationDO row, String newStatus, Long messageId,
                        int retryCount, LocalDateTime nextRetryAt, String lastErrorCode,
                        LocalDateTime sentAt, LocalDateTime updateTime) {
        int updated = notificationMapper.updateDeliveryIfMatch(new ApprovalNotificationDeliveryUpdate(
                row.getTenantId(), row.getId(), row.getVersion(), row.getStatusCode(), newStatus, messageId,
                retryCount, nextRetryAt, lastErrorCode, sentAt, "0", updateTime));
        require(updated == 1, "通知投递状态并发变化");
    }

    private static void validateRow(long tenantId, CutoverApprovalNotificationDO row) {
        require(row != null && Objects.equals(row.getTenantId(), tenantId) && row.getId() != null
                        && row.getApprovalInstanceId() != null && row.getApprovalNodeId() != null
                        && row.getRecipientUserId() != null && row.getRecipientUserId() > 0
                        && row.getDeliveryKey() != null && !row.getDeliveryKey().isBlank()
                        && row.getTemplateCode() != null && !row.getTemplateCode().isBlank()
                        && ("PENDING".equals(row.getStatusCode()) || "PENDING_RETRY".equals(row.getStatusCode()))
                        && row.getRetryCount() != null && row.getRetryCount() >= 0
                        && row.getVersion() != null && row.getVersion() >= 0,
                "通知待投递事实损坏");
    }

    static long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, 60L);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record DeliveryResult(int sent, int retried) { }

    private static final class NotificationProviderFailure extends RuntimeException {
        private NotificationProviderFailure(Throwable cause) { super(cause); }
    }
}
