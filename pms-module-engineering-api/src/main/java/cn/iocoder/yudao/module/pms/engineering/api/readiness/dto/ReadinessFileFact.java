package cn.iocoder.yudao.module.pms.engineering.api.readiness.dto;

public record ReadinessFileFact(
        Long itemId,
        Long artifactId,
        Integer versionNo,
        String referenceKey,
        Integer artifactVersion,
        Integer referenceVersion,
        Integer availabilityVersion,
        Long scopeVersion,
        String availabilityStatus,
        String referenceStatus) {}
