package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

import java.time.LocalDateTime;
import java.util.Map;

/** 指派事务内冻结的站内信投递载荷。 */
public record ProjectServiceManagerAssignedPayload(
        Long assignmentId,
        Long projectId,
        Long recipientUserId,
        String templateCode,
        Map<String, Object> templateParamsSnapshot,
        String assignmentType,
        String levelCode,
        LocalDateTime effectiveFrom) {
}
