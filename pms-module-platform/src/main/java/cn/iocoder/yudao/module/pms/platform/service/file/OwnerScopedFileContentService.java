package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@Service @RequiredArgsConstructor
public class OwnerScopedFileContentService implements OwnerScopedFileContentApi {
    private final List<OwnerScopedFileReadPolicy> policies;
    private final FileEvidenceApi evidence;
    private final FileVersionMapper versions;
    private final FileStorageContentApi storage;

    @Override @Transactional(rollbackFor = Exception.class)
    public Content read(Query query) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (query == null || actor == null || query.referenceId() == null || query.referenceId() <= 0)
            throw exception(FORBIDDEN);
        var owners = policies.stream().filter(policy -> policy.ownerCode().equals(query.ownerCode())).toList();
        if (owners.size() != 1) throw exception(FORBIDDEN);
        var document = evidence.inspectDocument(tenant, query.referenceId());
        if (document == null || !document.available()) throw exception(FORBIDDEN);
        var authorization = new OwnerScopedFileReadPolicy.Query(tenant, actor, query.businessKey(), document);
        var owner = owners.getFirst();
        owner.require(authorization);
        var fact = evidence.lockAndRevalidate(new FileEvidenceApi.Query(tenant, document.artifactId(), document.versionNo(),
                document.ownerContext(), document.objectType(), document.objectId(), document.purposeCode(), document.referenceKey(), document.sha256()));
        if (fact == null || !fact.valid()) throw exception(FORBIDDEN);
        var version = versions.selectForUpdate(new FileVersionLockQuery(tenant, document.artifactId(), document.versionNo()));
        if (version == null || !"AVAILABLE".equals(version.getAvailabilityStatusCode())
                || !Set.of("PASSED", "SKIPPED").contains(version.getScanStatusCode())
                || !Objects.equals(document.sha256(), version.getSha256()) || version.getSizeBytes() == null
                || version.getSizeBytes() < 1 || version.getSizeBytes() > 52_428_800L)
            throw exception(FORBIDDEN);
        byte[] bytes = storage.read(new FileStorageContentApi.Query(tenant, version.getInfraFileId(),
                version.getSizeBytes(), version.getSha256(), 52_428_800));
        owner.require(authorization);
        return new Content(document.name(), version.getDetectedMediaType() == null ? version.getDeclaredMediaType() : version.getDetectedMediaType(), bytes);
    }
}
