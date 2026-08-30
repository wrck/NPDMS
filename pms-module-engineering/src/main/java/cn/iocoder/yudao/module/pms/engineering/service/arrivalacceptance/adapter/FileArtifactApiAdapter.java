package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** PLT文件引用集合公开契约的只读生产适配。 */
@Component
@RequiredArgsConstructor
public class FileArtifactApiAdapter implements FileArtifactFactPort {

    private static final String OWNER_CONTEXT = "IMP";
    private static final String OBJECT_TYPE = "ARRIVAL_ACCEPTANCE";
    private static final String PURPOSE_CODE = "RECEIPT";

    private final FileArtifactApi fileArtifactApi;

    @Override
    public FileArtifactVersionFact inspectArrivalEvidence(Long artifactId, Integer versionNo,
                                                          Long arrivalAcceptanceId, String referenceKey) {
        return fileArtifactApi.inspect(new FileArtifactVersionQuery(
                artifactId, versionNo, OWNER_CONTEXT, OBJECT_TYPE,
                String.valueOf(arrivalAcceptanceId), PURPOSE_CODE, referenceKey, FileActionCodes.READ));
    }

    @Override
    public FileArtifactVersionFact lockAndRevalidateArrivalEvidence(
            ArrivalEvidenceExpectation expectation) {
        return fileArtifactApi.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(
                expectation.artifactId(), expectation.versionNo(), OWNER_CONTEXT, OBJECT_TYPE,
                String.valueOf(expectation.arrivalAcceptanceId()), PURPOSE_CODE,
                expectation.referenceKey(), FileActionCodes.READ,
                expectation.expectedFileFactVersion(), expectation.expectedScopeVersion()));
    }

    @Override
    public List<FileReferenceSetFact> inspectReferenceSets(List<FileReferenceSetKey> keys) {
        return List.copyOf(fileArtifactApi.inspectReferenceSets(
                new FileReferenceSetCollectionQuery(keys, FileActionCodes.READ)));
    }

    @Override
    public List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
            List<FileReferenceSetExpectation> expectations) {
        return List.copyOf(fileArtifactApi.lockAndRevalidateReferenceSets(
                new FileReferenceSetCollectionRevalidationQuery(expectations, FileActionCodes.READ)));
    }
}
