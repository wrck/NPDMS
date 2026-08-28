package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

public record CollectionConsumptionResultDTO(
        String platformTaskId,
        Long resultVersion,
        String status,
        boolean duplicate) {
}
