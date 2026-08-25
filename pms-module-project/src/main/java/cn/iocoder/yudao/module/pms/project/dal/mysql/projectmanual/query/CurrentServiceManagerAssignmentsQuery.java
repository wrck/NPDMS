package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
import java.util.Set;

/** 当前页项目节点的有效服务经理责任查询。 */
public record CurrentServiceManagerAssignmentsQuery(
        Long tenantId,
        Set<Long> projectIds,
        LocalDateTime effectiveAt) {
}
