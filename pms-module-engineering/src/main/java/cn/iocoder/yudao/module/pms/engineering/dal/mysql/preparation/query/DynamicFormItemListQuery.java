package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.util.List;

public record DynamicFormItemListQuery(Long tenantId, Long preparationId, List<Long> itemIds) {
    public DynamicFormItemListQuery {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
    }
}
