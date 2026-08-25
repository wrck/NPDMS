package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

/** 当前责任范围锁查询。 */
public record CurrentMemberResponsibilityQuery(
        Long projectId,
        String memberRole,
        String assignmentType,
        Long siteId,
        LocalDateTime effectiveAt) {
}
