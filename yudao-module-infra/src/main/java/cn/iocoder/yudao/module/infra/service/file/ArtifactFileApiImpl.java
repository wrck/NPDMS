package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.api.file.ArtifactFileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileCreateCommand;
import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileVersionDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class ArtifactFileApiImpl implements ArtifactFileApi {

    @Resource
    private ArtifactFileService artifactFileService;

    @Override
    public ArtifactFileVersionDTO store(ArtifactFileCreateCommand command, InputStream content) {
        return artifactFileService.store(command, content);
    }

    @Override
    public ArtifactFileVersionDTO getVersion(Long fileVersionId) {
        return artifactFileService.getVersion(fileVersionId);
    }
}