package cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query;

import java.util.Set;

public record PreparationWaiverBusinessQuery(Long tenantId, Long projectId, Set<String> itemCodes) {
    public PreparationWaiverBusinessQuery {
        itemCodes = itemCodes == null ? Set.of() : Set.copyOf(itemCodes);
    }
}
