package cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto;

public record AcceptanceActivityCompletionFact(
        String outcome,
        Long acceptanceId,
        Integer activityVersion,
        Long reportVersionId,
        Integer reportVersion) {
}
