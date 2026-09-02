package cn.iocoder.yudao.module.pms.cutover.service.spare.port;

/** CUT-08对PLT单文件不可变事实的消费端口；不提供生产fallback。 */
public interface CutoverSpareFilePort {

    FileFact inspect(FileExpectation expectation);

    FileFact lockAndRevalidate(FileExpectation expectation);

    record FileExpectation(Long tenantId, Long actorId, Long projectId, Long taskId,
                           Long artifactId, String referenceKey, Integer versionNo,
                           FileFactVersion fileFactVersion, Long scopeVersion) {
        public FileExpectation {
            positive(tenantId, "tenantId");
            positive(actorId, "actorId");
            positive(projectId, "projectId");
            positive(taskId, "taskId");
            positive(artifactId, "artifactId");
            text(referenceKey, 128, "referenceKey");
            positive(versionNo, "versionNo");
            require(fileFactVersion != null, "fileFactVersion");
            nonNegative(scopeVersion, "scopeVersion");
        }
    }

    record FileFact(Long artifactId, String referenceKey, Integer versionNo,
                    FileFactVersion fileFactVersion, Long scopeVersion, String displayName) {
        public FileFact {
            positive(artifactId, "artifactId");
            text(referenceKey, 128, "referenceKey");
            positive(versionNo, "versionNo");
            require(fileFactVersion != null, "fileFactVersion");
            nonNegative(scopeVersion, "scopeVersion");
            text(displayName, 255, "displayName");
        }
    }

    record FileFactVersion(Integer artifactVersion, Integer referenceVersion, Integer availabilityVersion) {
        public FileFactVersion {
            nonNegative(artifactVersion, "artifactVersion");
            nonNegative(referenceVersion, "referenceVersion");
            nonNegative(availabilityVersion, "availabilityVersion");
        }
    }

    private static void positive(Number value, String field) {
        require(value != null && value.longValue() > 0, field);
    }

    private static void nonNegative(Number value, String field) {
        require(value != null && value.longValue() >= 0, field);
    }

    private static void text(String value, int max, String field) {
        require(value != null && !value.isBlank() && value.equals(value.trim()) && value.length() <= max, field);
    }

    private static void require(boolean condition, String field) {
        if (!condition) throw new IllegalArgumentException("invalid " + field);
    }
}
