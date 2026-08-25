package cn.iocoder.yudao.module.pms.project.dal.mysql.projectattribute.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;

import java.time.LocalDateTime;
import java.util.Set;

/** 模板匹配历史分页查询；排序字段在进入Mapper前收敛到白名单。 */
public record ProjectTemplateMatchHistoryPageQuery(
        Long tenantId,
        Long projectId,
        PageParam pageParam,
        String triggerType,
        String matchResult,
        String impactResult,
        LocalDateTime occurredAtBegin,
        LocalDateTime occurredAtEnd,
        String orderBy,
        Boolean ascending) {

    private static final Set<String> ALLOWED_ORDER_FIELDS = Set.of("occurredAt", "recordedAt", "id");

    public ProjectTemplateMatchHistoryPageQuery {
        orderBy = orderBy == null || orderBy.isBlank() ? "occurredAt" : orderBy;
        if (!ALLOWED_ORDER_FIELDS.contains(orderBy)) {
            throw new IllegalArgumentException("不支持的模板匹配历史排序字段");
        }
        ascending = Boolean.TRUE.equals(ascending);
    }
}
