package cn.iocoder.yudao.module.pms.service.service.inspection;

import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InspectionFiles {
    private final FileArtifactApi files;
    private final AuthenticatedFileContentApi content;
    public record Reference(Long artifactId, Integer versionNo, String referenceKey) { }
    public record Query(Long taskId, String purpose, Reference reference) { }

    public FileArtifactVersionFact lock(Query query) {
        var ref = query.reference();
        InspectionException.require(ref != null, "缺少平台文件版本引用");
        var fact = files.inspect(new FileArtifactVersionQuery(ref.artifactId(), ref.versionNo(), "SRV", "INSPECTION_TASK",
                query.taskId().toString(), query.purpose(), ref.referenceKey(), FileActionCodes.DOWNLOAD));
        InspectionException.require(fact != null && "AVAILABLE".equals(fact.availabilityStatus())
                && "ACTIVE".equals(fact.referenceStatus()) && ("INSPECTION_" + query.purpose()).equals(fact.categoryCode()), "文件版本或业务归属不可用");
        return files.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(ref.artifactId(), ref.versionNo(), "SRV", "INSPECTION_TASK",
                query.taskId().toString(), query.purpose(), ref.referenceKey(), FileActionCodes.DOWNLOAD, fact.fileFactVersion(), fact.scopeVersion()));
    }

    public byte[] read(Query query) {
        var ref = query.reference();
        return content.read(new AuthenticatedFileContentApi.ReadCommand(new FileArtifactVersionQuery(ref.artifactId(), ref.versionNo(),
                "SRV", "INSPECTION_TASK", query.taskId().toString(), query.purpose(), ref.referenceKey(), FileActionCodes.DOWNLOAD), 52_428_800L));
    }
}
