package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;

/** PM-01：项目经理当前成员锁查询。 */
public record ProjectManagerMemberQuery(Long tenantId, Long projectId, LocalDateTime effectiveAt) { }
