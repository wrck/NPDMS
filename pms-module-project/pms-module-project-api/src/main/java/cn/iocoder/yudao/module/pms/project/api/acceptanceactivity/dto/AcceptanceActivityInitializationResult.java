package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto;

public record AcceptanceActivityInitializationResult(
        String outcome,
        Long acceptanceId,
        Integer activityVersion) {
}
