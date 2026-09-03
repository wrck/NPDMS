package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNodeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalNotificationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalNotificationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationClaimQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.ExternalApprovalNotificationDeliveryUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.notification.CutoverApprovalNotificationService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CutoverExternalApprovalNotificationTransactionExecutor {

    private static final Set<String> CHANNELS = Set.of("SMS", "EMAIL", "DINGTALK");
    private static final String OWNER_FACT_INVALID = "CUT_EXTERNAL_NOTIFICATION_FACT_INVALID";
    private static final String PROVIDER_UNAVAILABLE = "CUT_EXTERNAL_NOTIFICATION_PROVIDER_UNAVAILABLE";
    private static final String PROVIDER_RESULT_INVALID = "CUT_EXTERNAL_NOTIFICATION_RESULT_INVALID";

    private final CutoverApprovalNotificationMapper notificationMapper;
    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverApprovalNodeMapper nodeMapper;
    private final CutoverTaskMapper taskMapper;
    private final CutoverExternalApprovalNotificationPort port;

    public CutoverExternalApprovalNotificationTransactionExecutor(
            CutoverApprovalNotificationMapper notificationMapper,
            CutoverApprovalInstanceMapper instanceMapper,
            CutoverApprovalNodeMapper nodeMapper,
            CutoverTaskMapper taskMapper,
            CutoverExternalApprovalNotificationPort port) {
        this.notificationMapper = notificationMapper;
        this.instanceMapper = instanceMapper;
        this.nodeMapper = nodeMapper;
        this.taskMapper = taskMapper;
        this.port = port;
    }

    @Transactional
    public CutoverExternalApprovalNotificationService.DeliveryResult deliverBatch(
            long tenantId, LocalDateTime dueAt, int batchSize) {
        int accepted = 0;
        int unknown = 0;
        int retry = 0;
        var rows = notificationMapper.selectExternalDueForUpdateSkipLocked(
                new ExternalApprovalNotificationClaimQuery(tenantId, dueAt, batchSize));
        require(rows != null, "外部提醒领取结果损坏");
        for (CutoverApprovalNotificationDO row : rows) {
            ExternalApprovalNotificationRequest request;
            try {
                request = request(tenantId, row);
            } catch (RuntimeException exception) {
                retry(row, dueAt, OWNER_FACT_INVALID);
                retry++;
                continue;
            }
            ExternalApprovalNotificationResult result;
            try {
                result = port.send(request);
            } catch (RuntimeException exception) {
                retry(row, dueAt, PROVIDER_UNAVAILABLE);
                retry++;
                continue;
            }
            if (result instanceof ExternalApprovalNotificationResult.Accepted value) {
                update(row, "ACCEPTED", value.providerReferenceId(), row.getRetryCount(), null,
                        null, value.acceptedAt(), dueAt);
                accepted++;
            } else if (result instanceof ExternalApprovalNotificationResult.DeliveryUnknown value) {
                update(row, "DELIVERY_UNKNOWN", value.providerReferenceId(), row.getRetryCount(),
                        null, null, dueAt, dueAt);
                unknown++;
            } else if (result instanceof ExternalApprovalNotificationResult.ExplicitFailure value) {
                retry(row, dueAt, value.errorCode());
                retry++;
            } else {
                retry(row, dueAt, PROVIDER_RESULT_INVALID);
                retry++;
            }
        }
        return new CutoverExternalApprovalNotificationService.DeliveryResult(accepted, unknown, retry);
    }

    private ExternalApprovalNotificationRequest request(long tenantId, CutoverApprovalNotificationDO row) {
        requireValidRow(tenantId, row);
        CutoverApprovalInstanceDO root = instanceMapper.selectById(row.getApprovalInstanceId());
        CutoverApprovalNodeDO node = nodeMapper.selectById(row.getApprovalNodeId());
        require(root != null && node != null && Objects.equals(root.getTenantId(), tenantId)
                        && Objects.equals(node.getTenantId(), tenantId)
                        && Objects.equals(node.getApprovalInstanceId(), root.getId())
                        && Objects.equals(node.getId(), row.getApprovalNodeId())
                        && Objects.equals(node.getNodeNo(), nodeNo(row.getDeliveryKey())),
                "外部提醒审批身份损坏");
        CutoverTaskDO task = taskMapper.selectById(root.getTaskId());
        require(task != null && Objects.equals(task.getTenantId(), tenantId)
                        && Objects.equals(task.getId(), root.getTaskId())
                        && normalized(task.getTaskNo(), 64) && normalized(task.getTaskName(), 128)
                        && normalized(root.getGradeCode(), 1) && normalized(node.getNodeCode(), 32),
                "外部提醒任务身份损坏");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("taskCode", task.getTaskNo());
        variables.put("taskName", task.getTaskName());
        variables.put("grade", root.getGradeCode());
        variables.put("nodeName", nodeName(node.getNodeCode()));
        return new ExternalApprovalNotificationRequest(tenantId, row.getRecipientUserId(), row.getChannelCode(),
                row.getTemplateCode(), row.getDeliveryKey(), task.getId(), root.getId(), node.getNodeNo(),
                "/pms/cutover/cutover-task?taskId=" + task.getId(), variables, row.getCorrelationId());
    }

    private void retry(CutoverApprovalNotificationDO row, LocalDateTime dueAt, String errorCode) {
        int nextRetryCount = row.getRetryCount() + 1;
        update(row, "PENDING_RETRY", null, nextRetryCount,
                dueAt.plusMinutes(CutoverApprovalNotificationService.retryDelayMinutes(row.getRetryCount())),
                errorCode, dueAt, dueAt);
    }

    private void update(CutoverApprovalNotificationDO row, String newStatus, String providerReferenceId,
                        int retryCount, LocalDateTime nextRetryAt, String lastErrorCode,
                        LocalDateTime lastAttemptAt, LocalDateTime updateTime) {
        int updated = notificationMapper.updateExternalDeliveryIfMatch(new ExternalApprovalNotificationDeliveryUpdate(
                row.getTenantId(), row.getId(), row.getChannelCode(), row.getVersion(), row.getStatusCode(),
                newStatus, providerReferenceId, retryCount, nextRetryAt, lastErrorCode,
                lastAttemptAt, "0", updateTime));
        require(updated == 1, "外部提醒投递状态并发变化");
    }

    private static void requireValidRow(long tenantId, CutoverApprovalNotificationDO row) {
        require(row != null && Objects.equals(row.getTenantId(), tenantId) && row.getId() != null
                        && row.getApprovalInstanceId() != null && row.getApprovalNodeId() != null
                        && row.getRecipientUserId() != null && row.getRecipientUserId() > 0
                        && CHANNELS.contains(row.getChannelCode())
                        && "CUT_APPROVAL_PENDING_V2".equals(row.getTemplateCode())
                        && normalized(row.getDeliveryKey(), 256) && normalized(row.getCorrelationId(), 128)
                        && ("PENDING".equals(row.getStatusCode()) || "PENDING_RETRY".equals(row.getStatusCode()))
                        && row.getRetryCount() != null && row.getRetryCount() >= 0
                        && row.getVersion() != null && row.getVersion() >= 0,
                "外部提醒待投递事实损坏");
    }

    private static int nodeNo(String deliveryKey) {
        try {
            String[] parts = deliveryKey.split(":", -1);
            return parts.length == 5 ? Integer.parseInt(parts[2]) : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String nodeName(String nodeCode) {
        return switch (nodeCode) {
            case "INITIATOR" -> "发起人";
            case "SERVICE_MANAGER" -> "服务经理";
            case "SECOND_LINE" -> "二线审批";
            case "RND" -> "研发审批";
            default -> throw new IllegalStateException("外部提醒审批节点损坏");
        };
    }

    private static boolean normalized(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength && value.equals(value.trim());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
