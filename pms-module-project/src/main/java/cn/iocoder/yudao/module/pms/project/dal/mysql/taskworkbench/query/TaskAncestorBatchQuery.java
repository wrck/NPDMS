package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.util.Set;

/** LOCATE 当前命中页的批量祖先查询。 */
public record TaskAncestorBatchQuery(
        Long tenantId,
        Long projectId,
        Long actorId,
        boolean fullProjectAccess,
        Set<Long> descendantTaskIds) {
}
