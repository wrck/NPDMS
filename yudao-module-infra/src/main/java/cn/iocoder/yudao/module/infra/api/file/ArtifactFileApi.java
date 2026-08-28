package cn.iocoder.yudao.module.infra.api.file;

import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileCreateCommand;
import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileVersionDTO;

import java.io.InputStream;

public interface ArtifactFileApi {

    ArtifactFileVersionDTO store(ArtifactFileCreateCommand command, InputStream content);

    ArtifactFileVersionDTO getVersion(Long fileVersionId);
}