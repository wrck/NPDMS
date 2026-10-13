package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
import java.util.Set;

public record ProjectTreeManagersQuery(Long tenantId, Set<Long> projectIds, LocalDateTime effectiveAt) { }
