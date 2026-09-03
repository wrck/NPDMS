package cn.iocoder.yudao.module.pms.cutover.service.plan.port;

import static cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverPlanRules.require;

public interface CutoverPlanFilePort {
    FileFact inspect(Long tenantId, Long actorId, Long projectId, FileHandle handle);
    FileFact lockAndRevalidate(Long tenantId, Long actorId, Long projectId, FileHandle handle);
    FileFact downloadDraft(Long tenantId, Long actorId, Long projectId, Long planRevisionId);

    record FileHandle(Long artifactId, Integer versionNo, String referenceKey,
                      FileFactVersion fileFactVersion, Long scopeVersion) {
        public FileHandle { validate(artifactId, versionNo, referenceKey, fileFactVersion, scopeVersion); }
    }
    record FileFact(Long artifactId, Integer versionNo, String referenceKey,
                    FileFactVersion fileFactVersion, Long scopeVersion, String sha256) {
        public FileFact {
            validate(artifactId, versionNo, referenceKey, fileFactVersion, scopeVersion);
            require(sha256 != null && sha256.matches("[0-9a-f]{64}"), "sha256");
        }
        public FileHandle handle() { return new FileHandle(artifactId, versionNo, referenceKey, fileFactVersion, scopeVersion); }
    }
    record FileFactVersion(Integer artifactVersion, Integer referenceVersion, Integer availabilityVersion) {
        public FileFactVersion {
            require(artifactVersion != null && artifactVersion >= 0
                    && referenceVersion != null && referenceVersion >= 0
                    && availabilityVersion != null && availabilityVersion >= 0, "fileFactVersion");
        }
    }

    private static void validate(Long artifactId, Integer versionNo, String referenceKey,
                                 FileFactVersion version, Long scopeVersion) {
        require(artifactId != null && artifactId > 0 && versionNo != null && versionNo > 0, "file identity");
        require(referenceKey != null && !referenceKey.isBlank() && referenceKey.equals(referenceKey.trim())
                && referenceKey.length() <= 128, "referenceKey");
        require(version != null && version.artifactVersion() != null && version.artifactVersion() >= 0
                && version.referenceVersion() != null && version.referenceVersion() >= 0
                && version.availabilityVersion() != null && version.availabilityVersion() >= 0, "fileFactVersion");
        require(scopeVersion != null && scopeVersion >= 0, "scopeVersion");
    }
}
