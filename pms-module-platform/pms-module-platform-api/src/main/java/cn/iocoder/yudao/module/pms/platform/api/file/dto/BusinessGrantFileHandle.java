package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record BusinessGrantFileHandle(
        String policyKey, String fileSlotKey, Integer fileSequence,
        Long artifactId, Integer versionNo, String referenceKey,
        Integer artifactVersion, Integer referenceVersion,
        Integer availabilityVersion, Long scopeVersion, String sha256) {
}
