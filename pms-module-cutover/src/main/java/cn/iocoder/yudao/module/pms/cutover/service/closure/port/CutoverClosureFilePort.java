package cn.iocoder.yudao.module.pms.cutover.service.closure.port;

import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules;
import cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.AttachmentPurpose;

import static cn.iocoder.yudao.module.pms.cutover.service.closure.domain.CutoverClosureRules.*;

/** CUT闭环对PLT不可变单文件事实的消费端口；不提供生产fallback。 */
public interface CutoverClosureFilePort {

    FileFact inspect(FileExpectation expectation);

    FileFact lockAndRevalidate(FileExpectation expectation);

    record FileExpectation(Long tenantId, Long actorId, Long projectId, Long closureId,
                           AttachmentPurpose purposeCode, Long artifactId, Integer versionNo,
                           String referenceKey, FileFactVersion fileFactVersion,
                           Long scopeVersion, String sha256) {
        public FileExpectation {
            positive(tenantId, "tenantId");
            positive(actorId, "actorId");
            positive(projectId, "projectId");
            positive(closureId, "closureId");
            requireValue(purposeCode, "purposeCode");
            positive(artifactId, "artifactId");
            positive(versionNo, "versionNo");
            normalizedText(referenceKey, 128, "referenceKey");
            requireValue(fileFactVersion, "fileFactVersion");
            nonNegative(scopeVersion, "scopeVersion");
            CutoverClosureRules.sha256(sha256);
        }
    }

    record FileFact(Long artifactId, Integer versionNo, String referenceKey,
                    FileFactVersion fileFactVersion, Long scopeVersion, String sha256) {
        public FileFact {
            positive(artifactId, "artifactId");
            positive(versionNo, "versionNo");
            normalizedText(referenceKey, 128, "referenceKey");
            requireValue(fileFactVersion, "fileFactVersion");
            nonNegative(scopeVersion, "scopeVersion");
            CutoverClosureRules.sha256(sha256);
        }
    }

    record FileFactVersion(Integer artifactVersion, Integer referenceVersion, Integer availabilityVersion) {
        public FileFactVersion {
            require(artifactVersion != null && artifactVersion >= 0, "artifactVersion");
            require(referenceVersion != null && referenceVersion >= 0, "referenceVersion");
            require(availabilityVersion != null && availabilityVersion >= 0, "availabilityVersion");
        }
    }
}
