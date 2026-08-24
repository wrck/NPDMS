package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.util.Set;

/** ProjectTreeScope内的责任分布节点分页。 */
public record ServiceManagerResponsibilityPageQuery(
        Long tenantId,
        Set<Long> projectIds,
        Long filterProjectId,
        int offset,
        int limit) {
}
