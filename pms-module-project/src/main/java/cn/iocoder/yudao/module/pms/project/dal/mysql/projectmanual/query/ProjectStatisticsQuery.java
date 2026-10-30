package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
import java.util.Set;

/** 当前主体全部可见项目的状态卡统计，不接受列表筛选条件。 */
public record ProjectStatisticsQuery(Long tenantId, Set<Long> visibleProjectIds, LocalDateTime effectiveAt) { }
