package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileArtifactVersionFact(
        Long artifactId,
        Integer versionNo,
        String referenceKey,
        String categoryCode,
        String name,
        Long sizeBytes,
        String mediaType,
        String sha256,
        String availabilityStatus,
        String referenceStatus,
        FileFactVersion fileFactVersion,
        Long scopeVersion) {
}
