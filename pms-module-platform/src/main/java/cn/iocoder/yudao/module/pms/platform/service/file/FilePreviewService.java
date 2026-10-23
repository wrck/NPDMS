package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.AuthenticatedFileContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_ACCESS_GRANT_INVALID;

/** 授权预览派生内容，不登记新业务版本、不向外部文档服务传输文件。 */
@Service
@RequiredArgsConstructor
public class FilePreviewService {
    private final AuthenticatedFileContentApi content;
    private final FileArtifactApi artifacts;
    private final FileVersionMapper versions;
    private final FileOfficePdfConverter office;
    private final OperationAuditApi audit;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    public record Preview(byte[] bytes, String mediaType) { }

    public Preview preview(FileAccessTicketService.AccessCommand command) {
        String correlation = UUID.randomUUID().toString();
        try {
            if (!FileActionCodes.PREVIEW.equals(command.operationCode())) throw exception(FILE_ACCESS_GRANT_INVALID);
            var query = new FileArtifactVersionQuery(command.artifactId(), command.versionNo(), command.ownerContext(),
                    command.objectType(), command.objectId(), command.purposeCode(), command.referenceKey(), FileActionCodes.PREVIEW);
            byte[] original = content.read(new AuthenticatedFileContentApi.ReadCommand(query, 52_428_800L));
            var version = versions.selectOne(new FileVersionLockQuery(command.tenantId(), command.artifactId(), command.versionNo()));
            if (version == null) throw exception(FILE_ACCESS_GRANT_INVALID);
            Preview preview;
            if (FileOfficePdfConverter.supports(version.getDetectedMediaType())) {
                preview = new Preview(office.convert(new FileOfficePdfConverter.Document(original, version.getDetectedMediaType())), "application/pdf");
            } else if (FileAccessTicketService.isPreviewable(version.getDetectedMediaType())) {
                preview = new Preview(original, version.getDetectedMediaType());
            } else throw exception(FILE_ACCESS_GRANT_INVALID);
            // 转换可能耗时，返回前再次校验当前用户、Owner精确版本、引用与可用性。
            if (!permissions.hasAnyPermissions(command.actorUserId(), "pms:file:preview")) throw exception(FILE_ACCESS_GRANT_INVALID);
            artifacts.inspect(query);
            record(command, correlation, "SUCCESS");
            return preview;
        } catch (RuntimeException ex) {
            record(command, correlation, "REJECTED");
            throw ex;
        }
    }

    private void record(FileAccessTicketService.AccessCommand command, String correlation, String outcome) {
        audit.record(command.tenantId(), command.actorUserId(), correlation, "FILE_PREVIEW", "FileArtifact",
                String.valueOf(command.artifactId()), outcome, Map.of("versionNo", command.versionNo(),
                        "ownerContext", command.ownerContext(), "objectType", command.objectType(), "objectId", command.objectId(),
                        "purposeCode", command.purposeCode(), "referenceKey", command.referenceKey()));
    }
}
