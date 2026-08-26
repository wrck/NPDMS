package cn.iocoder.yudao.module.pms.customer.api.masterdata.dto;

public record CustomerMasterDataCommand(
        Long tenantId,
        Long customerId,
        String customerCode,
        String customerName,
        String shortName,
        String sourceKey,
        String sourceVersion,
        String operationId,
        Long expectedVersion) {
}
