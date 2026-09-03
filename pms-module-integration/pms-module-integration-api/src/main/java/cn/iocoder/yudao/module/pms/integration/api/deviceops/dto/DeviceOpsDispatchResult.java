package cn.iocoder.yudao.module.pms.integration.api.deviceops.dto;

public record DeviceOpsDispatchResult(
        String platformTaskId,
        String externalTaskId,
        String externalStatus,
        boolean accepted,
        boolean replayed,
        String traceId) {
}
