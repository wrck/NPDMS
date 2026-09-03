package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

public record CollectionConsumptionCommand(
        String platformTaskId,
        String consumerContext,
        String consumerObjectType,
        String consumerObjectId,
        Long resultVersion,
        String traceId) {
}
