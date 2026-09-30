package cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query;

import java.util.List;

/** 显式原生成果清单页；objectIds 为 null 表示项目全量，空列表表示无对象。 */
public record SolutionResultInventoryQuery(Long tenantId, Long projectId, List<Long> objectIds,
                                           Long afterId, int limit, boolean historical) {
    public SolutionResultInventoryQuery { objectIds = objectIds == null ? null : List.copyOf(objectIds); }
}
