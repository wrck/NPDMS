package cn.iocoder.yudao.module.pms.commerce.service.scope;

public record DeliveryScopeCommandResult(Long deliveryScopeId, Long allocationVersion, boolean replayed) {
}
