package cn.iocoder.yudao.module.pms.customer.service.customer.command;

public record ProjectCustomerExtensionCommand(Long tenantId, Long projectId, String remark,
                                              Long expectedVersion, String idempotencyKey) { }
