package cn.iocoder.yudao.module.pms.platform.api.file;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;

import java.util.List;

public interface FileArtifactApi {

    FileArtifactVersionFact inspect(FileArtifactVersionQuery query);

    FileArtifactVersionFact lockAndRevalidate(FileArtifactVersionRevalidationQuery query);

    List<FileArtifactVersionFact> attachExistingVersions(AttachExistingFileVersionsCommand command);
}
