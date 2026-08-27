package cn.iocoder.yudao.module.pms.asset.service.projection;

public record DeviceProjectionReconciliationCommand(
        Long tenantId,
        String deviceSn,
        Long actorId,
        String correlationId) {
}
