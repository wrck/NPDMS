package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

/** 任务命令锁后重验当前项目成员主体。 */
public record ActiveProjectMemberForUpdateQuery(
        Long tenantId, Long projectId, Long userId, LocalDateTime effectiveAt) {
}
