package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.adapter;

import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.FileArtifactFactPort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** PLT文件引用集合公开契约的只读生产适配。 */
@Component
@RequiredArgsConstructor
public class FileArtifactApiAdapter implements FileArtifactFactPort {

    private final FileArtifactApi fileArtifactApi;

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
