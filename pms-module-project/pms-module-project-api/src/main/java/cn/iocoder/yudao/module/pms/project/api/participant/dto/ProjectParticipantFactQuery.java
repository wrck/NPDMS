package cn.iocoder.yudao.module.pms.project.api.participant.dto;

import java.time.LocalDateTime;
import java.util.Set;

/** 项目参与人时态快照查询；tenantId只取受信调用上下文。 */
public record ProjectParticipantFactQuery(
        Long projectId,
        Long subjectUserId,
        Set<String> requiredRoleCodes,
        LocalDateTime checkedAt) {

    public ProjectParticipantFactQuery {
        if (requiredRoleCodes != null) {
            requiredRoleCodes = Set.copyOf(requiredRoleCodes);
        }
    }

}
