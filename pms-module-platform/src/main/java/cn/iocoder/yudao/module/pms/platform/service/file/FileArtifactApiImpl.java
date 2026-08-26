package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_ARTIFACT_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_FACT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_REFERENCE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_UNAVAILABLE;

@Service
public class FileArtifactApiImpl implements FileArtifactApi {

    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;

    public FileArtifactApiImpl(FileBusinessObjectPolicyRegistry policyRegistry,
                               FileArtifactMapper artifactMapper,
                               FileVersionMapper versionMapper,
                               FileReferenceMapper referenceMapper) {
        this.policyRegistry = policyRegistry;
        this.artifactMapper = artifactMapper;
        this.versionMapper = versionMapper;
        this.referenceMapper = referenceMapper;
    }

    @Override
    public FileArtifactVersionFact inspect(FileArtifactVersionQuery query) {
        TrustedActor actor = trustedActor();
        FileBusinessObjectPolicyFact policy = policyRegistry.inspect(policyQuery(actor, query));
        FileArtifactDO artifact = requireArtifact(artifactMapper.selectOne(
                new FileArtifactLockQuery(actor.tenantId(), query.artifactId())), query, policy);
        FileVersionDO version = requireVersion(versionMapper.selectOne(
                new FileVersionLockQuery(actor.tenantId(), query.artifactId(), query.versionNo())));
        FileReferenceDO reference = requireReference(referenceMapper.selectExact(
                new ExactFileReferenceQuery(actor.tenantId(), query.ownerContext(), query.objectType(),
                        query.objectId(), query.purposeCode(), query.referenceKey())), query, policy);
        return toFact(artifact, version, reference, policy.scopeVersion());
    }

    @Override
    @Transactional
    public FileArtifactVersionFact lockAndRevalidate(FileArtifactVersionRevalidationQuery query) {
        TrustedActor actor = trustedActor();
        FileBusinessObjectPolicyFact policy = policyRegistry.lockAndRevalidate(
                new FileBusinessObjectPolicyRevalidationQuery(actor.tenantId(), actor.userId(),
                        query.ownerContext(), query.objectType(), query.objectId(), query.purposeCode(),
                        query.referenceKey(), query.requiredAction(), query.expectedScopeVersion()));
        FileArtifactDO artifact = requireArtifact(artifactMapper.selectForUpdate(
                new FileArtifactLockQuery(actor.tenantId(), query.artifactId())), query.toInspectionQuery(), policy);
        FileVersionDO version = requireVersion(versionMapper.selectForUpdate(
                new FileVersionLockQuery(actor.tenantId(), query.artifactId(), query.versionNo())));
        FileReferenceDO reference = requireReference(referenceMapper.selectForUpdate(
                new FileReferenceLockQuery(actor.tenantId(), query.ownerContext(), query.objectType(),
                        query.objectId(), query.purposeCode(), query.referenceKey())), query.toInspectionQuery(), policy);
        FileArtifactVersionFact fact = toFact(artifact, version, reference, policy.scopeVersion());
        if (!query.expectedFileFactVersion().equals(fact.fileFactVersion())) {
            throw exception(FILE_FACT_VERSION_CONFLICT);
        }
        return fact;
    }

    private FileBusinessObjectPolicyQuery policyQuery(TrustedActor actor, FileArtifactVersionQuery query) {
        return new FileBusinessObjectPolicyQuery(actor.tenantId(), actor.userId(), query.ownerContext(),
                query.objectType(), query.objectId(), query.purposeCode(), query.referenceKey(),
                query.requiredAction());
    }

    private FileArtifactDO requireArtifact(FileArtifactDO artifact, FileArtifactVersionQuery query,
                                           FileBusinessObjectPolicyFact policy) {
        if (artifact == null || !"ACTIVE".equals(artifact.getLifecycleStatusCode())) {
            throw exception(FILE_ARTIFACT_NOT_FOUND);
        }
        if (!query.ownerContext().equals(artifact.getOwnerContext())
                || !policy.allowedCategoryCodes().contains(artifact.getCategoryCode())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        return artifact;
    }

    private FileVersionDO requireVersion(FileVersionDO version) {
        if (version == null) {
            throw exception(FILE_VERSION_NOT_FOUND);
        }
        if (!"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            throw exception(FILE_VERSION_UNAVAILABLE);
        }
        return version;
    }

    private FileReferenceDO requireReference(FileReferenceDO reference, FileArtifactVersionQuery query,
                                             FileBusinessObjectPolicyFact policy) {
        if (reference == null || !"ACTIVE".equals(reference.getStatusCode())) {
            throw exception(FILE_REFERENCE_NOT_FOUND);
        }
        if (!query.artifactId().equals(reference.getArtifactId())
                || !query.versionNo().equals(reference.getFileVersionNo())
                || !policy.scopeVersion().equals(reference.getScopeVersion())) {
            throw exception(FILE_REFERENCE_NOT_FOUND);
        }
        return reference;
    }

    private FileArtifactVersionFact toFact(FileArtifactDO artifact, FileVersionDO version,
                                           FileReferenceDO reference, Long scopeVersion) {
        return new FileArtifactVersionFact(artifact.getId(), version.getVersionNo(), reference.getReferenceKey(),
                artifact.getCategoryCode(), artifact.getName(), version.getSizeBytes(),
                version.getDetectedMediaType(), version.getSha256(), version.getAvailabilityStatusCode(),
                reference.getStatusCode(), new FileFactVersion(artifact.getVersion(), reference.getVersion(),
                version.getAvailabilityVersion()), scopeVersion);
    }

    private TrustedActor trustedActor() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null || userId <= 0) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        return new TrustedActor(TenantContextHolder.getRequiredTenantId(), userId);
    }

    private record TrustedActor(Long tenantId, Long userId) {
    }
}
