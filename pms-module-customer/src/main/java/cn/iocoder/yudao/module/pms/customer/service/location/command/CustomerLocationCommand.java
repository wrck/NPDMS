package cn.iocoder.yudao.module.pms.customer.service.location.command;

public record CustomerLocationCommand(
        Long tenantId,
        Long customerId,
        String locationType,
        Long locationId,
        Integer sourceVersion,
        String operationId) {
}
