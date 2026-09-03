package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;

import java.util.List;

/** 到货签收只读文件引用集合端口。 */
public interface FileArtifactFactPort {

    FileArtifactVersionFact inspectArrivalEvidence(Long artifactId, Integer versionNo,
                                                    Long arrivalAcceptanceId, String referenceKey);

    FileArtifactVersionFact lockAndRevalidateArrivalEvidence(ArrivalEvidenceExpectation expectation);

    List<FileReferenceSetFact> inspectReferenceSets(List<FileReferenceSetKey> keys);

    List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
            List<FileReferenceSetExpectation> expectations);

    record ArrivalEvidenceExpectation(Long artifactId, Integer versionNo,
                                      Long arrivalAcceptanceId, String referenceKey,
                                      FileFactVersion expectedFileFactVersion,
                                      Long expectedScopeVersion) {

        public ArrivalEvidenceExpectation {
            if (artifactId == null || artifactId <= 0 || versionNo == null || versionNo <= 0
                    || arrivalAcceptanceId == null || arrivalAcceptanceId <= 0
                    || referenceKey == null || referenceKey.isBlank()
                    || expectedFileFactVersion == null
                    || expectedScopeVersion == null || expectedScopeVersion < 0) {
                throw new IllegalArgumentException("invalid arrival evidence expectation");
            }
            referenceKey = referenceKey.trim();
        }
    }
}
