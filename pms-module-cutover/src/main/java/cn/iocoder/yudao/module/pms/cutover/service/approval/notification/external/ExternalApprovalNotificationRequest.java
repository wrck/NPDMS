package cn.iocoder.yudao.module.pms.cutover.service.approval.notification.external;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record ExternalApprovalNotificationRequest(
        Long tenantId,
        Long recipientUserId,
        String channel,
        String templateCode,
        String deliveryKey,
        Long taskId,
        Long approvalInstanceId,
        Integer nodeNo,
        String taskLink,
        Map<String, String> variables,
        String correlationId) {

    private static final Set<String> CHANNELS = Set.of("SMS", "EMAIL", "DINGTALK");
    private static final Set<String> VARIABLE_KEYS = Set.of("taskCode", "taskName", "grade", "nodeName");

    public ExternalApprovalNotificationRequest {
        require(tenantId != null && tenantId > 0, "tenantId invalid");
        require(recipientUserId != null && recipientUserId > 0, "recipientUserId invalid");
        require(CHANNELS.contains(channel), "channel invalid");
        require("CUT_APPROVAL_PENDING_V2".equals(templateCode), "templateCode invalid");
        require(taskId != null && taskId > 0, "taskId invalid");
        require(approvalInstanceId != null && approvalInstanceId > 0, "approvalInstanceId invalid");
        require(nodeNo != null && nodeNo > 0, "nodeNo invalid");
        require(validDeliveryKey(deliveryKey, approvalInstanceId, nodeNo, channel), "deliveryKey invalid");
        require(normalized(taskLink, 256)
                        && taskLink.equals("/pms/cutover/cutover-task?taskId=" + taskId),
                "taskLink invalid");
        require(variables != null && variables.keySet().equals(VARIABLE_KEYS)
                && variables.values().stream().allMatch(value -> normalized(value, 128)), "variables invalid");
        require(normalized(correlationId, 128), "correlationId invalid");
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }

    private static boolean validDeliveryKey(String value, Long approvalInstanceId, Integer nodeNo, String channel) {
        if (!normalized(value, 256)) return false;
        String[] parts = value.split(":", -1);
        if (parts.length != 5 || !"CUT_APPROVAL_EXT".equals(parts[0])
                || !approvalInstanceId.toString().equals(parts[1])
                || !nodeNo.toString().equals(parts[2]) || !channel.equals(parts[4])) return false;
        try {
            return Integer.parseInt(parts[3]) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean normalized(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength && value.equals(value.trim());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
