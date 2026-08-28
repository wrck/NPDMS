package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

public record ReadinessSourceFact(
        Long sourceReferenceId,
        Long itemId,
        String sourceTypeCode,
        String sourceReferenceKey,
        String normalizedResultCode,
        String sourceFactVersion,
        String sourceWatermark,
        String syncStatusCode,
        Integer version) {}
