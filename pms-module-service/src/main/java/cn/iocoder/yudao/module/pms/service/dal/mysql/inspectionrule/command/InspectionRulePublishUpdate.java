package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command;

import java.time.LocalDateTime;

public record InspectionRulePublishUpdate(
        Long tenantId,
        Long revisionId,
        Integer expectedVersion,
        String categoryNameSnapshot,
        String severityNameSnapshot,
        Long publishedBy,
        LocalDateTime publishedAt) {
}
