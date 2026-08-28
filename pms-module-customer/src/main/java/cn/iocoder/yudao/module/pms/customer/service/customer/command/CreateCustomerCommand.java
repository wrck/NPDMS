package cn.iocoder.yudao.module.pms.customer.service.customer.command;

import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerSourceType;

public record CreateCustomerCommand(
        Long tenantId,
        String code,
        String name,
        String shortName,
        String remark,
        CustomerSourceType sourceType,
        String sourceKey,
        String sourceVersion,
        String temporaryReason,
        boolean reconciliationPending,
        String departmentCode,
        String marketCode,
        String systemCode,
        String expendCode,
        String industryCode,
        String idempotencyKey) {
}
