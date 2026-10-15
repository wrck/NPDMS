package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query;
import java.util.Set;
public record ServiceIssueWorkbenchQuery(Long tenantId, Long actorId, Set<Long> visibleProjectIds, int limit) { }
