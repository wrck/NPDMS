package cn.iocoder.yudao.module.pms.customer.service.customer.command;

public record CustomerCommandResult(
        Long customerId,
        Long version,
        boolean replayed) {
}
