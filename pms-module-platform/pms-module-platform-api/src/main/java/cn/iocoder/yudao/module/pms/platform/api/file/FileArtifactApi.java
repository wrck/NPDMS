package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArchiveReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.GeneratedBusinessFileCommand;

import java.util.List;

public interface FileArtifactApi {

    FileArtifactVersionFact inspect(FileArtifactVersionQuery query);

    FileArtifactVersionFact lockAndRevalidate(FileArtifactVersionRevalidationQuery query);

    List<FileReferenceSetFact> inspectReferenceSets(FileReferenceSetCollectionQuery query);

    List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
            FileReferenceSetCollectionRevalidationQuery query);

    List<FileArtifactVersionFact> attachExistingVersions(AttachExistingFileVersionsCommand command);

    FileArchiveReferenceSetFact archiveReferenceSets(ArchiveFileReferenceSetsCommand command);

    FileArtifactVersionFact createGeneratedBusinessFile(GeneratedBusinessFileCommand command);
}
