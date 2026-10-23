package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
public record ProjectMemberSelectionQuery(Long tenantId, Long projectId, Long assignmentId, LocalDateTime effectiveAt) {}
