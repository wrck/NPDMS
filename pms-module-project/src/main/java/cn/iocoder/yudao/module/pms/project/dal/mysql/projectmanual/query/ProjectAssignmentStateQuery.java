package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

/** 项目节点当前指派状态查询。 */
public record ProjectAssignmentStateQuery(Long projectId, LocalDateTime effectiveAt) {
}
