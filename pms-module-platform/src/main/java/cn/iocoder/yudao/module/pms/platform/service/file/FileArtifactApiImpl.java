package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.ExactFileReferenceQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceSetQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
    private final ExistingFileVersionAttachmentService attachmentService;

    public FileArtifactApiImpl(FileBusinessObjectPolicyRegistry policyRegistry,
                               FileArtifactMapper artifactMapper,
                               FileVersionMapper versionMapper,
                               FileReferenceMapper referenceMapper,
                               ExistingFileVersionAttachmentService attachmentService) {
        this.policyRegistry = policyRegistry;
        this.artifactMapper = artifactMapper;
        this.versionMapper = versionMapper;
        this.referenceMapper = referenceMapper;
        this.attachmentService = attachmentService;
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

    @Override
    @Transactional(readOnly = true)
    public List<FileReferenceSetFact> inspectReferenceSets(FileReferenceSetCollectionQuery query) {
        TrustedActor actor = trustedActor();
        List<FileReferenceSetKey> keys = query.collectionKeys().stream().sorted().toList();
        Map<FileReferenceSetKey, FileBusinessObjectPolicyFact> policies = new LinkedHashMap<>();
        for (FileReferenceSetKey key : keys) {
            policies.put(key, policyRegistry.inspectReferenceSet(
                    new FileBusinessObjectReferenceSetQuery(actor.tenantId(), actor.userId(), key,
                            FileActionCodes.READ)));
        }
        List<FileReferenceSetFact> result = new ArrayList<>(keys.size());
        for (FileReferenceSetKey key : keys) {
            FileBusinessObjectPolicyFact policy = policies.get(key);
            List<FileArtifactVersionFact> facts = facts(actor.tenantId(), key, policy,
                    referenceMapper.selectActiveSet(setQuery(actor.tenantId(), key)), false,
                    Map.of(), Map.of());
            result.add(new FileReferenceSetFact(key, policy.scopeVersion(), facts));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional
    public List<FileReferenceSetFact> lockAndRevalidateReferenceSets(
            FileReferenceSetCollectionRevalidationQuery query) {
        TrustedActor actor = trustedActor();
        List<FileReferenceSetExpectation> expectations = query.collections().stream()
                .sorted(Comparator.comparing(FileReferenceSetExpectation::key)).toList();
        Map<FileReferenceSetKey, FileBusinessObjectPolicyFact> policies = new LinkedHashMap<>();
        for (FileReferenceSetExpectation expectation : expectations) {
            policies.put(expectation.key(), policyRegistry.lockAndRevalidateReferenceSet(
                    new FileBusinessObjectReferenceSetRevalidationQuery(actor.tenantId(), actor.userId(),
                            expectation.key(), FileActionCodes.READ, expectation.expectedScopeVersion())));
        }

        Map<FileReferenceSetKey, List<FileReferenceDO>> snapshots = new LinkedHashMap<>();
        expectations.forEach(expectation -> snapshots.put(expectation.key(),
                referenceMapper.selectActiveSet(setQuery(actor.tenantId(), expectation.key()))));
        TreeSet<Long> artifactIds = new TreeSet<>();
        TreeSet<VersionKey> versionKeys = new TreeSet<>();
        expectations.forEach(expectation -> expectation.expectedActiveFacts()
                .forEach(fact -> addKeys(fact.artifactId(), fact.versionNo(), artifactIds, versionKeys)));
        snapshots.values().forEach(rows -> rows.forEach(row ->
                addKeys(row.getArtifactId(), row.getFileVersionNo(), artifactIds, versionKeys)));

        Map<Long, FileArtifactDO> artifacts = new LinkedHashMap<>();
        artifactIds.forEach(id -> artifacts.put(id, artifactMapper.selectForUpdate(
                new FileArtifactLockQuery(actor.tenantId(), id))));
        Map<VersionKey, FileVersionDO> versions = new LinkedHashMap<>();
        versionKeys.forEach(key -> versions.put(key, versionMapper.selectForUpdate(
                new FileVersionLockQuery(actor.tenantId(), key.artifactId(), key.versionNo()))));

        List<FileReferenceSetFact> result = new ArrayList<>(expectations.size());
        for (FileReferenceSetExpectation expectation : expectations) {
            FileReferenceSetKey key = expectation.key();
            List<FileReferenceDO> locked = referenceMapper.selectSetForUpdate(setQuery(actor.tenantId(), key))
                    .stream().filter(row -> "ACTIVE".equals(row.getStatusCode())).toList();
            List<FileArtifactVersionFact> actual = facts(actor.tenantId(), key, policies.get(key), locked,
                    true, artifacts, versions);
            if (!expectation.expectedActiveFacts().equals(actual)) {
                throw exception(FILE_FACT_VERSION_CONFLICT);
            }
            result.add(new FileReferenceSetFact(key, policies.get(key).scopeVersion(), actual));
        }
        return List.copyOf(result);
    }

    @Override
    public List<FileArtifactVersionFact> attachExistingVersions(AttachExistingFileVersionsCommand command) {
        return attachmentService.attach(command);
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

    private List<FileArtifactVersionFact> facts(Long tenantId, FileReferenceSetKey key,
                                                FileBusinessObjectPolicyFact policy,
                                                List<FileReferenceDO> references, boolean locked,
                                                Map<Long, FileArtifactDO> lockedArtifacts,
                                                Map<VersionKey, FileVersionDO> lockedVersions) {
        List<FileArtifactVersionFact> facts = new ArrayList<>(references.size());
        for (FileReferenceDO reference : references) {
            if (!policy.scopeVersion().equals(reference.getScopeVersion())) {
                throw exception(FILE_FACT_VERSION_CONFLICT);
            }
            FileArtifactDO artifact = locked ? lockedArtifacts.get(reference.getArtifactId())
                    : artifactMapper.selectOne(new FileArtifactLockQuery(tenantId, reference.getArtifactId()));
            FileVersionDO version = locked
                    ? lockedVersions.get(new VersionKey(reference.getArtifactId(), reference.getFileVersionNo()))
                    : versionMapper.selectOne(new FileVersionLockQuery(
                    tenantId, reference.getArtifactId(), reference.getFileVersionNo()));
            requireSetArtifact(artifact, key, policy);
            requireVersion(version);
            facts.add(toFact(artifact, version, reference, policy.scopeVersion()));
        }
        return facts.stream().sorted(Comparator.comparing(FileArtifactVersionFact::referenceKey)).toList();
    }

    private void requireSetArtifact(FileArtifactDO artifact, FileReferenceSetKey key,
                                    FileBusinessObjectPolicyFact policy) {
        if (artifact == null || !"ACTIVE".equals(artifact.getLifecycleStatusCode())) {
            throw exception(FILE_ARTIFACT_NOT_FOUND);
        }
        if (!key.ownerContext().equals(artifact.getOwnerContext())
                || !policy.allowedCategoryCodes().contains(artifact.getCategoryCode())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
    }

    private FileReferenceSetQuery setQuery(Long tenantId, FileReferenceSetKey key) {
        return new FileReferenceSetQuery(tenantId, key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode());
    }

    private void addKeys(Long artifactId, Integer versionNo, TreeSet<Long> artifactIds,
                         TreeSet<VersionKey> versionKeys) {
        if (artifactId == null || versionNo == null) throw exception(FILE_FACT_VERSION_CONFLICT);
        artifactIds.add(artifactId);
        versionKeys.add(new VersionKey(artifactId, versionNo));
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

    private record VersionKey(Long artifactId, Integer versionNo) implements Comparable<VersionKey> {
        @Override
        public int compareTo(VersionKey other) {
            int result = artifactId.compareTo(other.artifactId);
            return result == 0 ? versionNo.compareTo(other.versionNo) : result;
        }
    }
}
