package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileCreateCommand;
import cn.iocoder.yudao.module.infra.api.file.dto.ArtifactFileVersionDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class ArtifactFileService {

    static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    @Resource
    private FileConfigService fileConfigService;
    @Resource
    private FileArtifactMapper fileArtifactMapper;
    @Resource
    private FileVersionMapper fileVersionMapper;

    @SneakyThrows
    @Transactional
    public ArtifactFileVersionDTO store(ArtifactFileCreateCommand command, InputStream content) {
        validate(command);
        FileArtifactDO artifact = fileArtifactMapper.selectBySource(command.sourceSystem(), command.sourceArtifactKey());
        if (artifact != null) {
            FileVersionDO existing = fileVersionMapper.selectByArtifactAndDigest(artifact.getId(), command.declaredSha256());
            if (existing != null) {
                return toDTO(artifact, existing);
            }
        }
        Path temporaryFile = Files.createTempFile("artifact-file-", ".tmp");
        try {
            ContentDigest contentDigest = copyAndDigest(content, temporaryFile);
            if (contentDigest.size() != command.declaredSize()
                    || !contentDigest.sha256().equalsIgnoreCase(command.declaredSha256())) {
                throw new IllegalArgumentException("文件声明与实际内容不一致");
            }
            if (artifact == null) {
                artifact = new FileArtifactDO().setSourceSystem(command.sourceSystem())
                        .setSourceArtifactKey(command.sourceArtifactKey()).setName(command.name())
                        .setAccessScope(command.accessScope());
                artifact.setTenantId(command.tenantId());
                fileArtifactMapper.insert(artifact);
            }
            String storageKey = command.directory() + "/" + command.idempotencyKey() + "-" + command.name();
            FileClient client = fileConfigService.getMasterFileClient();
            String url;
            try (InputStream uploadStream = Files.newInputStream(temporaryFile)) {
                url = client.upload(uploadStream, contentDigest.size(), storageKey, command.contentType());
            }
            FileVersionDO version = new FileVersionDO().setArtifactId(artifact.getId()).setConfigId(client.getId())
                    .setContentSha256(contentDigest.sha256()).setSize(contentDigest.size())
                    .setContentType(command.contentType()).setStorageKey(storageKey).setUrl(url).setScanStatus("CLEAN");
            version.setTenantId(command.tenantId());
            fileVersionMapper.insert(version);
            return toDTO(artifact, version);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public ArtifactFileVersionDTO getVersion(Long fileVersionId) {
        FileVersionDO version = fileVersionMapper.selectById(fileVersionId);
        if (version == null) {
            return null;
        }
        return toDTO(fileArtifactMapper.selectById(version.getArtifactId()), version);
    }

    static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @SneakyThrows
    private static ContentDigest copyAndDigest(InputStream content, Path temporaryFile) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        byte[] buffer = new byte[8192];
        try (DigestInputStream digestStream = new DigestInputStream(content, digest);
             var output = Files.newOutputStream(temporaryFile, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = digestStream.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("文件超过 50MB");
                }
                output.write(buffer, 0, read);
            }
        }
        return new ContentDigest(total, HexFormat.of().formatHex(digest.digest()));
    }

    private static void validate(ArtifactFileCreateCommand command) {
        if (command.declaredSize() < 0 || command.declaredSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过 50MB");
        }
        if (command.sourceSystem() == null || command.sourceArtifactKey() == null
                || command.idempotencyKey() == null || command.declaredSha256() == null) {
            throw new IllegalArgumentException("文件命令缺少必填字段");
        }
    }

    private static ArtifactFileVersionDTO toDTO(FileArtifactDO artifact, FileVersionDO version) {
        return new ArtifactFileVersionDTO(artifact.getId(), version.getId(), artifact.getName(),
                version.getContentType(), version.getSize(), version.getContentSha256(), version.getStorageKey(),
                version.getScanStatus(), artifact.getAccessScope());
    }

    private record ContentDigest(long size, String sha256) {
    }
}