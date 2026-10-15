package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthenticatedFileContentService implements AuthenticatedFileContentApi {
    private final FileArtifactApi artifacts;
    private final FileVersionMapper versions;
    private final FileStorageContentApi storage;
    private final PermissionApi permissions;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public byte[] read(ReadCommand command) {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (command == null || command.file() == null || command.maxBytes() < 1 || command.maxBytes() > 52_428_800L
                || !FileActionCodes.DOWNLOAD.equals(command.file().requiredAction()) || actor == null
                || !permissions.hasAnyPermissions(actor, "pms:file:download"))
            throw new IllegalArgumentException("授权文件读取参数或权限无效");
        var query = command.file();
        var fact = artifacts.inspect(query);
        artifacts.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(query.artifactId(), query.versionNo(),
                query.ownerContext(), query.objectType(), query.objectId(), query.purposeCode(), query.referenceKey(),
                query.requiredAction(), fact.fileFactVersion(), fact.scopeVersion()));
        var version = versions.selectForUpdate(new FileVersionLockQuery(
                TenantContextHolder.getRequiredTenantId(), query.artifactId(), query.versionNo()));
        if (version == null || !"AVAILABLE".equals(version.getAvailabilityStatusCode())
                || !Set.of("PASSED", "SKIPPED").contains(version.getScanStatusCode()) || version.getSizeBytes() == null
                || version.getSizeBytes() < 1 || version.getSizeBytes() > command.maxBytes())
            throw new IllegalStateException("文件不可用或超过本次读取上限");
        return storage.read(new FileStorageContentApi.Query(TenantContextHolder.getRequiredTenantId(),
                version.getInfraFileId(), version.getSizeBytes(), version.getSha256(), Math.toIntExact(command.maxBytes())));
    }
}
