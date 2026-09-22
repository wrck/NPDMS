package cn.iocoder.yudao.module.pms.customer.api.masterdata.dto;

public record CustomerMasterDataCommand(
        Long tenantId,
        Long customerId,
        String customerCode,
        String customerName,
        String shortName,
        String contactPhone,
        String contactEmail,
        String address,
        String departmentCode,
        String departmentName,
        String marketCode,
        String marketName,
        String systemCode,
        String systemName,
        String expendCode,
        String expendName,
        String industryCode,
        String industryName,
        String lifecycleStatus,
        String sourceKey,
        String sourceVersion,
        java.time.LocalDateTime dataAsOf,
        String operationId,
        Long expectedVersion) {
}
