package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;

import java.util.List;

/** 到货签收只读文件引用集合端口。 */
public interface FileArtifactFactPort {

    List<FileReferenceSetFact> inspectReferenceSets(List<FileReferenceSetKey> keys);

    List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
            List<FileReferenceSetExpectation> expectations);
}
