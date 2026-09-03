package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record CollectionBatchProjectionUpdate(
        Long tenantId,
        Long batchId,
        int successDelta,
        int failureDelta) {
}
