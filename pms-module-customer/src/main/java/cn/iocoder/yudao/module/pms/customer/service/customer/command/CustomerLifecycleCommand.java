package cn.iocoder.yudao.module.pms.customer.service.customer.command;

public record CustomerLifecycleCommand(
        Long tenantId,
        Long customerId,
        String reason,
        Long expectedVersion,
        String idempotencyKey) {
}
