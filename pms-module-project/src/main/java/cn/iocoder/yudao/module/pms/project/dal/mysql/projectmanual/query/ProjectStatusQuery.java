package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
import java.util.Set;

/** 已授权列表页或详情的项目状态展示事实。 */
public record ProjectStatusQuery(Long tenantId, Set<Long> projectIds, LocalDateTime effectiveAt) { }
