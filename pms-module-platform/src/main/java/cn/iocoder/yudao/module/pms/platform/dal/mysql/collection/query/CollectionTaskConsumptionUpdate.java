package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record CollectionTaskConsumptionUpdate(
        Long tenantId,
        String platformTaskId,
        Long expectedResultVersion,
        String status,
        Long consumedResultVersion) {
}
