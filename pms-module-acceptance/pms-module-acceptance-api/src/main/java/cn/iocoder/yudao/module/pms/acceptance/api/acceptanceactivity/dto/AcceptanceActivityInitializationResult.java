package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto;

public record AcceptanceActivityInitializationResult(
        String outcome,
        Long acceptanceId,
        Long activityVersion) {
}
