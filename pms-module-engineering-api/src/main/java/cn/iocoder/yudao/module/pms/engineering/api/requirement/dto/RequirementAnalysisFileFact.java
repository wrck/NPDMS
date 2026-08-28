package cn.iocoder.yudao.module.pms.engineering.api.requirement.dto;

public record RequirementAnalysisFileFact(
        Long artifactId,
        Integer versionNo,
        String referenceKey,
        Integer artifactVersion,
        Integer referenceVersion,
        Integer availabilityVersion,
        Long scopeVersion) {
}
