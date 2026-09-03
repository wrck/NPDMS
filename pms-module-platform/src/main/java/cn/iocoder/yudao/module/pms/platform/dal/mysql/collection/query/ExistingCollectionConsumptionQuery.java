package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record ExistingCollectionConsumptionQuery(
        Long tenantId,
        String platformTaskId,
        String consumerContext,
        String consumerObjectType,
        String consumerObjectId,
        Long resultVersion) {
}
