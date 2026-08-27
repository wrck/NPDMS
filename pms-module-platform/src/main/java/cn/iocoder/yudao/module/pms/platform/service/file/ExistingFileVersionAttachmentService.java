package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionItem;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.AttachExistingFileVersionsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ExistingFileReferenceTarget;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectReferenceSetRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import cn.iocoder.yudao.module.pms.platform.service.command.PlatformTransactionalOutboxWriter;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_ARTIFACT_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_FACT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_REFERENCE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_REFERENCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_UNAVAILABLE;

@Service
public class ExistingFileVersionAttachmentService {

    private static final Comparator<BusinessKey> BUSINESS_KEY_ORDER = Comparator
            .comparing(BusinessKey::ownerContext)
            .thenComparing(BusinessKey::objectType)
            .thenComparing(BusinessKey::objectId)
            .thenComparing(BusinessKey::purposeCode)
            .thenComparing(BusinessKey::referenceKey);
    private static final Comparator<ProviderLock> PROVIDER_LOCK_ORDER = Comparator
            .comparing(ProviderLock::key, BUSINESS_KEY_ORDER)
            .thenComparing(ProviderLock::action);

    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final FileEventFactory eventFactory;
    private final PlatformTransactionalOutboxWriter outboxWriter;

    public ExistingFileVersionAttachmentService(
            FileBusinessObjectPolicyRegistry policyRegistry,
            FileArtifactMapper artifactMapper,
            FileVersionMapper versionMapper,
            FileReferenceMapper referenceMapper,
            FileEventFactory eventFactory,
            PlatformTransactionalOutboxWriter outboxWriter) {
        this.policyRegistry = policyRegistry;
        this.artifactMapper = artifactMapper;
        this.versionMapper = versionMapper;
        this.referenceMapper = referenceMapper;
        this.eventFactory = eventFactory;
        this.outboxWriter = outboxWriter;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<FileArtifactVersionFact> attach(AttachExistingFileVersionsCommand command) {
        if (command == null) {
            throw exception(FILE_COMMAND_INVALID);
        }
        TrustedActor actor = trustedActor();
        List<AttachExistingFileVersionItem> items = command.items();
        validateDistinctReferences(items);
        lockNamespaces(actor, items);

        FileBusinessObjectPolicyFact[] sourcePolicies = new FileBusinessObjectPolicyFact[items.size()];
        FileBusinessObjectPolicyFact[] targetPolicies = new FileBusinessObjectPolicyFact[items.size()];
        List<ProviderLock> providerLocks = new ArrayList<>(items.size() * 2);
        for (int index = 0; index < items.size(); index++) {
            int itemIndex = index;
            AttachExistingFileVersionItem item = items.get(index);
            providerLocks.add(sourceProviderLock(actor, item, fact -> sourcePolicies[itemIndex] = fact));
            providerLocks.add(targetProviderLock(actor, item.target(), fact -> targetPolicies[itemIndex] = fact));
        }
        providerLocks.stream().sorted(PROVIDER_LOCK_ORDER).forEach(ProviderLock::lock);
        for (int index = 0; index < items.size(); index++) {
            requireAttachmentPolicies(sourcePolicies[index], targetPolicies[index]);
        }
        Map<Long, FileArtifactDO> artifacts = lockArtifacts(actor.tenantId(), items);
        Map<VersionKey, FileVersionDO> versions = lockVersions(actor.tenantId(), items);
        Map<BusinessKey, FileReferenceDO> references = lockReferences(actor.tenantId(), items);

        List<FileArtifactVersionFact> result = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            AttachExistingFileVersionItem item = items.get(index);
            FileArtifactDO artifact = requireArtifact(artifacts.get(item.source().artifactId()),
                    item, sourcePolicies[index], targetPolicies[index]);
            FileVersionDO version = requireVersion(versions.get(
                    new VersionKey(item.source().artifactId(), item.source().versionNo())), targetPolicies[index]);
            requireSourceReference(references.get(BusinessKey.source(item)), item, sourcePolicies[index],
                    artifact, version);
            BusinessKey targetKey = BusinessKey.target(item.target());
            FileReferenceDO target = references.get(targetKey);
            if (target == null) {
                target = insertTarget(actor, command.operationId(), item, targetPolicies[index], artifact, version);
            } else {
                requireReplayTarget(target, item, targetPolicies[index]);
            }
            result.add(toFact(artifact, version, target, targetPolicies[index].scopeVersion()));
        }
        return List.copyOf(result);
    }

    private ProviderLock sourceProviderLock(TrustedActor actor, AttachExistingFileVersionItem item,
                                            Consumer<FileBusinessObjectPolicyFact> factConsumer) {
        var source = item.source();
        BusinessKey key = BusinessKey.source(item);
        return new ProviderLock(key, FileActionCodes.READ, () -> factConsumer.accept(
                policyRegistry.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                        actor.tenantId(), actor.userId(), key.ownerContext(), key.objectType(), key.objectId(),
                        key.purposeCode(), key.referenceKey(), FileActionCodes.READ,
                        source.expectedScopeVersion()))));
    }

    private ProviderLock targetProviderLock(TrustedActor actor, ExistingFileReferenceTarget target,
                                            Consumer<FileBusinessObjectPolicyFact> factConsumer) {
        BusinessKey key = BusinessKey.target(target);
        return new ProviderLock(key, FileActionCodes.REFERENCE, () -> factConsumer.accept(
                policyRegistry.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                        actor.tenantId(), actor.userId(), key.ownerContext(), key.objectType(), key.objectId(),
                        key.purposeCode(), key.referenceKey(), FileActionCodes.REFERENCE,
                target.expectedScopeVersion()))));
    }

    private void lockNamespaces(TrustedActor actor, List<AttachExistingFileVersionItem> items) {
        Map<NamespaceAction, Long> scopes = new HashMap<>();
        for (AttachExistingFileVersionItem item : items) {
            putScope(scopes, new NamespaceAction(BusinessKey.source(item).namespace(), FileActionCodes.READ),
                    item.source().expectedScopeVersion());
            putScope(scopes, new NamespaceAction(BusinessKey.target(item.target()).namespace(),
                    FileActionCodes.REFERENCE), item.target().expectedScopeVersion());
        }
        scopes.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                policyRegistry.lockAndRevalidateReferenceSet(
                        new FileBusinessObjectReferenceSetRevalidationQuery(actor.tenantId(), actor.userId(),
                                entry.getKey().key(), entry.getKey().action(), entry.getValue())));
    }

    private void putScope(Map<NamespaceAction, Long> scopes, NamespaceAction key, Long scopeVersion) {
        Long existing = scopes.putIfAbsent(key, scopeVersion);
        if (existing != null && !existing.equals(scopeVersion)) throw exception(FILE_FACT_VERSION_CONFLICT);
    }

    private Map<Long, FileArtifactDO> lockArtifacts(Long tenantId, List<AttachExistingFileVersionItem> items) {
        Map<Long, FileArtifactDO> locked = new LinkedHashMap<>();
        items.stream().map(item -> item.source().artifactId()).distinct().sorted()
                .forEach(artifactId -> locked.put(artifactId, artifactMapper.selectForUpdate(
                        new FileArtifactLockQuery(tenantId, artifactId))));
        return locked;
    }

    private Map<VersionKey, FileVersionDO> lockVersions(Long tenantId,
                                                       List<AttachExistingFileVersionItem> items) {
        Map<VersionKey, FileVersionDO> locked = new LinkedHashMap<>();
        items.stream().map(item -> new VersionKey(item.source().artifactId(), item.source().versionNo()))
                .distinct().sorted().forEach(key -> locked.put(key, versionMapper.selectForUpdate(
                        new FileVersionLockQuery(tenantId, key.artifactId(), key.versionNo()))));
        return locked;
    }

    private Map<BusinessKey, FileReferenceDO> lockReferences(Long tenantId,
                                                             List<AttachExistingFileVersionItem> items) {
        Set<BusinessKey> keys = new HashSet<>();
        items.forEach(item -> {
            keys.add(BusinessKey.source(item));
            keys.add(BusinessKey.target(item.target()));
        });
        Map<BusinessKey, FileReferenceDO> locked = new HashMap<>();
        keys.stream().sorted(BUSINESS_KEY_ORDER).forEach(key -> locked.put(key,
                referenceMapper.selectForUpdate(new FileReferenceLockQuery(tenantId, key.ownerContext(),
                        key.objectType(), key.objectId(), key.purposeCode(), key.referenceKey()))));
        return locked;
    }

    private FileArtifactDO requireArtifact(FileArtifactDO artifact, AttachExistingFileVersionItem item,
                                           FileBusinessObjectPolicyFact sourcePolicy,
                                           FileBusinessObjectPolicyFact targetPolicy) {
        if (artifact == null || !"ACTIVE".equals(artifact.getLifecycleStatusCode())) {
            throw exception(FILE_ARTIFACT_NOT_FOUND);
        }
        if (!item.source().ownerContext().equals(artifact.getOwnerContext())
                || !sourcePolicy.allowedCategoryCodes().contains(artifact.getCategoryCode())
                || !targetPolicy.allowedCategoryCodes().contains(artifact.getCategoryCode())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        if (!item.source().expectedFileFactVersion().artifactVersion().equals(artifact.getVersion())) {
            throw exception(FILE_FACT_VERSION_CONFLICT);
        }
        return artifact;
    }

    private FileVersionDO requireVersion(FileVersionDO version, FileBusinessObjectPolicyFact targetPolicy) {
        if (version == null) {
            throw exception(FILE_VERSION_NOT_FOUND);
        }
        if (!"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            throw exception(FILE_VERSION_UNAVAILABLE);
        }
        if (targetPolicy.allowedMediaTypes().stream().noneMatch(mediaType ->
                mediaType.equalsIgnoreCase(version.getDetectedMediaType()))
                || version.getSizeBytes() == null || version.getSizeBytes() > targetPolicy.maxSizeBytes()) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        return version;
    }

    private void requireSourceReference(FileReferenceDO reference, AttachExistingFileVersionItem item,
                                        FileBusinessObjectPolicyFact sourcePolicy, FileArtifactDO artifact,
                                        FileVersionDO version) {
        FileFactVersion expected = item.source().expectedFileFactVersion();
        if (reference == null) {
            throw exception(FILE_REFERENCE_NOT_FOUND);
        }
        if (!"ACTIVE".equals(reference.getStatusCode())
                || !artifact.getId().equals(reference.getArtifactId())
                || !version.getVersionNo().equals(reference.getFileVersionNo())
                || !sourcePolicy.scopeVersion().equals(reference.getScopeVersion())
                || !expected.referenceVersion().equals(reference.getVersion())
                || !expected.availabilityVersion().equals(version.getAvailabilityVersion())) {
            throw exception(FILE_FACT_VERSION_CONFLICT);
        }
    }

    private void requireAttachmentPolicies(FileBusinessObjectPolicyFact sourcePolicy,
                                           FileBusinessObjectPolicyFact targetPolicy) {
        if (!"IMMUTABLE".equals(sourcePolicy.referenceMutability())
                || !"MUTABLE".equals(targetPolicy.referenceMutability())
                || !"MULTIPLE".equals(targetPolicy.cardinality())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
    }

    private FileReferenceDO insertTarget(TrustedActor actor, String operationId,
                                         AttachExistingFileVersionItem item,
                                         FileBusinessObjectPolicyFact targetPolicy,
                                         FileArtifactDO artifact, FileVersionDO version) {
        ExistingFileReferenceTarget key = item.target();
        FileReferenceDO target = new FileReferenceDO();
        target.setOwnerContext(key.ownerContext());
        target.setObjectType(key.objectType());
        target.setObjectId(key.objectId());
        target.setPurposeCode(key.purposeCode());
        target.setReferenceKey(key.referenceKey());
        target.setArtifactId(artifact.getId());
        target.setFileVersionNo(version.getVersionNo());
        target.setSensitivityCode(targetPolicy.sensitivityCode());
        target.setStatusCode("ACTIVE");
        target.setScopeVersion(targetPolicy.scopeVersion());
        target.setVersion(0);
        target.setCreator(String.valueOf(actor.userId()));
        target.setUpdater(String.valueOf(actor.userId()));
        target.setTenantId(actor.tenantId());
        if (referenceMapper.insert(target) != 1) {
            throw new IllegalStateException("FILE_REFERENCE_CREATE_FAILED");
        }
        LocalDateTime occurredAt = LocalDateTime.now();
        outboxWriter.write(actor.tenantId(), eventFactory.referenceAttached(actor.tenantId(), target.getId(),
                        artifact.getId(), version.getVersionNo(), key.ownerContext(), key.objectType(),
                        key.objectId(), key.purposeCode(), occurredAt, operationId),
                "FileArtifact", String.valueOf(artifact.getId()), occurredAt);
        return target;
    }

    private void requireReplayTarget(FileReferenceDO target, AttachExistingFileVersionItem item,
                                     FileBusinessObjectPolicyFact targetPolicy) {
        if (!"ACTIVE".equals(target.getStatusCode())
                || !item.source().artifactId().equals(target.getArtifactId())
                || !item.source().versionNo().equals(target.getFileVersionNo())
                || !targetPolicy.scopeVersion().equals(target.getScopeVersion())) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
    }

    private FileArtifactVersionFact toFact(FileArtifactDO artifact, FileVersionDO version,
                                           FileReferenceDO reference, Long scopeVersion) {
        return new FileArtifactVersionFact(artifact.getId(), version.getVersionNo(), reference.getReferenceKey(),
                artifact.getCategoryCode(), artifact.getName(), version.getSizeBytes(),
                version.getDetectedMediaType(), version.getSha256(), version.getAvailabilityStatusCode(),
                reference.getStatusCode(), new FileFactVersion(artifact.getVersion(), reference.getVersion(),
                version.getAvailabilityVersion()), scopeVersion);
    }

    private void validateDistinctReferences(List<AttachExistingFileVersionItem> items) {
        Set<BusinessKey> sources = new HashSet<>();
        Set<BusinessKey> targets = new HashSet<>();
        for (AttachExistingFileVersionItem item : items) {
            sources.add(BusinessKey.source(item));
            if (!targets.add(BusinessKey.target(item.target()))) {
                throw exception(FILE_COMMAND_INVALID);
            }
        }
        if (targets.stream().anyMatch(sources::contains)) {
            throw exception(FILE_COMMAND_INVALID);
        }
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

    private record ProviderLock(BusinessKey key, String action, Runnable operation) {
        private void lock() {
            operation.run();
        }
    }

    private record BusinessKey(String ownerContext, String objectType, String objectId,
                               String purposeCode, String referenceKey) {
        private FileReferenceSetKey namespace() {
            return new FileReferenceSetKey(ownerContext, objectType, objectId, purposeCode);
        }
        private static BusinessKey source(AttachExistingFileVersionItem item) {
            var source = item.source();
            return new BusinessKey(source.ownerContext(), source.objectType(), source.objectId(),
                    source.purposeCode(), source.referenceKey());
        }

        private static BusinessKey target(ExistingFileReferenceTarget target) {
            return new BusinessKey(target.ownerContext(), target.objectType(), target.objectId(),
                    target.purposeCode(), target.referenceKey());
        }
    }

    private record NamespaceAction(FileReferenceSetKey key, String action)
            implements Comparable<NamespaceAction> {
        @Override
        public int compareTo(NamespaceAction other) {
            int result = key.compareTo(other.key);
            return result == 0 ? action.compareTo(other.action) : result;
        }
    }

    private record VersionKey(Long artifactId, Integer versionNo) implements Comparable<VersionKey> {
        @Override
        public int compareTo(VersionKey other) {
            int artifactComparison = artifactId.compareTo(other.artifactId);
            return artifactComparison != 0 ? artifactComparison : versionNo.compareTo(other.versionNo);
        }
    }
}
