package cn.iocoder.yudao.module.pms.platform.service.export;

import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionArtifactBindingQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionAvailabilityUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.FileUploadApplicationService;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PlatformExportFileWriter implements ExportFileWriter {

    private final FileUploadApplicationService uploadService;
    private final FileArtifactApi fileArtifactApi;
    private final FileUploadSessionMapper sessionMapper;
    private final FileVersionMapper versionMapper;
    private final FileStorageReceiptApi storageReceiptApi;

    @Override
    public WrittenExportFile write(Command command) {
        String objectId = String.valueOf(command.taskId());
        String referenceKey = "export-task-" + command.taskId();
        FileUploadInitialized initialized = uploadService.initialize(new FileUploadInitializeCommand(
                command.tenantId(), command.actorUserId(), command.operationId() + ":init", "CREATE_ARTIFACT",
                null, null, "PLATFORM", "EXPORT_TASK", objectId, "EXPORT_FILE", referenceKey,
                "export-task-" + command.taskId() + ".csv", "EXPORT_FILE", (long) command.content().length,
                "text/csv", null));
        FileUploadCompleted completed = uploadService.complete(new FileUploadCompleteCommand(command.tenantId(),
                command.actorUserId(), command.operationId() + ":complete", initialized.artifactId(),
                initialized.sessionId(), new BytesMultipartFile(command.content()), null));
        FileArtifactVersionFact fact = fileArtifactApi.inspect(new FileArtifactVersionQuery(completed.artifactId(),
                completed.versionNo(), "PLATFORM", "EXPORT_TASK", objectId, "EXPORT_FILE", referenceKey,
                FileActionCodes.READ));
        return new WrittenExportFile(fact.artifactId(), fact.versionNo(), fact.referenceKey(),
                fact.fileFactVersion().artifactVersion(), fact.fileFactVersion().referenceVersion(),
                fact.fileFactVersion().availabilityVersion(), fact.sha256());
    }

    @Override
    public void expire(Command command, WrittenExportFile file) {
        String objectId = String.valueOf(command.taskId());
        FileUploadSessionDO session = sessionMapper.selectArtifactBindingForUpdate(
                new FileUploadSessionArtifactBindingQuery(command.tenantId(), file.artifactId(), "PLATFORM",
                        "EXPORT_TASK", objectId, "EXPORT_FILE", file.referenceKey()));
        FileVersionDO version = versionMapper.selectForUpdate(
                new FileVersionLockQuery(command.tenantId(), file.artifactId(), file.versionNo()));
        if (session == null || version == null || !"AVAILABLE".equals(version.getAvailabilityStatusCode())
                || !file.availabilityVersion().equals(version.getAvailabilityVersion())) {
            throw new IllegalStateException("统一导出文件到期身份冲突");
        }
        storageReceiptApi.delete(session.getStorageOperationId());
        if (versionMapper.updateAvailabilityIfMatch(new FileVersionAvailabilityUpdate(command.tenantId(),
                file.artifactId(), file.versionNo(), version.getAvailabilityVersion(), "AVAILABLE", "UNAVAILABLE",
                "EXPORT_TTL_EXPIRED", LocalDateTime.now())) != 1) {
            throw new IllegalStateException("统一导出文件到期版本冲突");
        }
    }

    private record BytesMultipartFile(byte[] content) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return "export.csv"; }
        @Override public String getContentType() { return "text/csv"; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
