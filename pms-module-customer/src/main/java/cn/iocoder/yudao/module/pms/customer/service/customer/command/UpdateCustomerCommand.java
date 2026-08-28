package cn.iocoder.yudao.module.pms.customer.service.customer.command;

import java.util.Set;

public record UpdateCustomerCommand(
        Long tenantId,
        Long customerId,
        String name,
        String shortName,
        String remark,
        String departmentCode,
        String marketCode,
        String systemCode,
        String expendCode,
        String industryCode,
        Set<String> changedFields,
        Long expectedVersion,
        String idempotencyKey) {

    public UpdateCustomerCommand(
            Long tenantId,
            Long customerId,
            String name,
            String shortName,
            String remark,
            Set<String> changedFields,
            Long expectedVersion,
            String idempotencyKey) {
        this(tenantId, customerId, name, shortName, remark, null, null, null, null, null,
                changedFields, expectedVersion, idempotencyKey);
    }
}
