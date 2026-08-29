package cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto;

public record AcceptanceActivityCompletionFact(
        String outcome,
        Long acceptanceId,
        Integer activityVersion,
        Long reportVersionId,
        Integer reportVersion) {
}
