package cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.dto;

public record SatisfactionTemplateResolveQuery(Long tenantId, String projectType, String signingMode,
        String implementationMode, String businessPurposeCode, String applicableTimingCode) {
}
