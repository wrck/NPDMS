package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import lombok.Builder;
import java.time.LocalDateTime;
import java.util.Set;

/** 当前项目参与人时态区间查询。 */
@Builder
public record ProjectParticipantFactLookupQuery(
        Long tenantId,
        Long projectId,
        Long subjectUserId,
        Set<String> requiredRoleCodes,
        LocalDateTime effectiveAt) {
}
