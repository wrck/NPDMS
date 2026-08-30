package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

public record SatisfactionTemplateApplicabilityQuery(
        Long tenantId,
        String projectType,
        String signingMode,
        String implementationMode,
        String businessPurposeCode,
        String applicableTimingCode,
        Integer priority) {
}
