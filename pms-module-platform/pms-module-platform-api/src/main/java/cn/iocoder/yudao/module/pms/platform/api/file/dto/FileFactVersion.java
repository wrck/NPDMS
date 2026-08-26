package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record FileFactVersion(
        Integer artifactVersion,
        Integer referenceVersion,
        Integer availabilityVersion) {

    public FileFactVersion {
        if (artifactVersion == null || artifactVersion < 0
                || referenceVersion == null || referenceVersion < 0
                || availabilityVersion == null || availabilityVersion < 0) {
            throw new IllegalArgumentException("invalid file fact version");
        }
    }
}
