package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record CollectionReconciliationDueQuery(Long tenantId, long afterId, int limit) {
    public CollectionReconciliationDueQuery {
        if (tenantId == null || afterId < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("COLLECTION_RECONCILIATION_QUERY_INVALID");
        }
    }
}
