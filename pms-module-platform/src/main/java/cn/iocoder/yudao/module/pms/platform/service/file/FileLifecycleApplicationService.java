package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArchiveRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArchiveRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileArchivedMessage;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileReferenceDetachedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class FileLifecycleApplicationService {

    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final SecurityFrameworkService securityFrameworkService;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final FileArchiveRecordMapper archiveRecordMapper;
    private final FileEventFactory eventFactory;

    public LifecycleResult detach(DetachFileReferenceCommand command) {
        if (command == null || command.referenceId() == null || command.referenceId() <= 0
                || command.expectedReferenceVersion() == null || command.expectedReferenceVersion() < 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return execute(command.tenantId(), command.actorUserId(), command.idempotencyKey(),
                "PLT:FILE:DETACH", "pms:file:manage", requestDigest(command),
                "FILE_REFERENCE_DETACH", String.valueOf(command.referenceId()),
                facts -> detach(command, facts), facts -> detachSuccess(command, facts));
    }

    public LifecycleResult deleteDraft(DeleteDraftFileCommand command) {
        if (command == null || command.artifactId() == null || command.artifactId() <= 0
                || command.expectedArtifactVersion() == null || command.expectedArtifactVersion() < 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return execute(command.tenantId(), command.actorUserId(), command.idempotencyKey(),
                "PLT:FILE:DELETE_DRAFT", "pms:file:manage", requestDigest(command),
                "FILE_DRAFT_DELETE", String.valueOf(command.artifactId()),
                facts -> deleteDraft(command, facts), facts -> genericSuccess("FILE_DRAFT_DELETE", facts));
    }

    public LifecycleResult changeAvailability(ChangeFileAvailabilityCommand command) {
        if (command == null || command.artifactId() == null || command.artifactId() <= 0
                || command.versionNo() == null || command.versionNo() <= 0
                || command.expectedAvailabilityVersion() == null || command.expectedAvailabilityVersion() < 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return execute(command.tenantId(), command.actorUserId(), command.idempotencyKey(),
                "PLT:FILE:AVAILABILITY", "pms:file:archive", requestDigest(command),
                "FILE_VERSION_AVAILABILITY_CHANGE", String.valueOf(command.artifactId()),
                facts -> changeAvailability(command, facts),
                facts -> genericSuccess("FILE_VERSION_AVAILABILITY_CHANGE", facts));
    }

    public LifecycleResult archive(ArchiveFileReferenceCommand command) {
        if (command == null || command.referenceId() == null || command.referenceId() <= 0
                || command.expectedReferenceVersion() == null || command.expectedReferenceVersion() < 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return execute(command.tenantId(), command.actorUserId(), command.idempotencyKey(),
                "PLT:FILE:ARCHIVE", "pms:file:archive", requestDigest(command),
                "FILE_REFERENCE_ARCHIVE", String.valueOf(command.referenceId()),
                facts -> archive(command, facts), facts -> archiveSuccess(command, facts));
    }

    private LifecycleResult execute(Long tenantId, Long actorId, String key, String scope,
                                    String permission, String digest, String operationCode,
                                    String resourceKey, Operation operation, Success success) {
        validateActor(tenantId, actorId, key);
        AtomicReference<LifecycleFacts> facts = new AtomicReference<>();
        try {
            if (!securityFrameworkService.hasPermission(permission)) throw exception(FILE_SCOPE_FORBIDDEN);
            var result = commandExecutionApi.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(tenantId, scope, actorId, key),
                    digest, LifecycleResult.class, () -> {
                        LifecycleFacts value = operation.run(facts);
                        facts.set(value);
                        return value.result();
                    }, ignored -> success.create(requireFacts(facts)));
            if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                    || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || result.response() == null) throw exception(FILE_COMMAND_INVALID);
            return result.response();
        } catch (RuntimeException failure) {
            auditRejected(tenantId, actorId, key, operationCode, resourceKey, failure);
            throw failure;
        }
    }

    private LifecycleFacts detach(DetachFileReferenceCommand command, AtomicReference<LifecycleFacts> ignored) {
        ValidatedKey key = key(command.ownerContext(), command.objectType(), command.objectId(),
                command.purposeCode(), command.referenceKey());
        FileBusinessObjectPolicyFact policy = authorize(command.tenantId(), command.actorUserId(), key,
                FileActionCodes.DETACH);
        requireMutable(policy);
        FileReferenceDO reference = lockReference(command.tenantId(), key);
        if (!command.referenceId().equals(reference.getId()) || !"ACTIVE".equals(reference.getStatusCode())
                || !command.expectedReferenceVersion().equals(reference.getVersion())) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        if (referenceMapper.updateStateIfMatch(new FileReferenceStateUpdate(command.tenantId(), reference.getId(),
                reference.getVersion(), "ACTIVE", "DETACHED", policy.scopeVersion(), command.actorUserId(),
                text(command.reason()), now)) != 1) throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        LifecycleFacts base = facts(command.tenantId(), command.actorUserId(), command.idempotencyKey(), reference,
                "ACTIVE", "DETACHED", reference.getVersion(), reference.getVersion() + 1,
                policy.scopeVersion(), now, null, null);
        return new LifecycleFacts(base.result(), base.tenantId(), base.actorUserId(), base.operationId(),
                base.artifactId(), base.versionNo(), base.referenceId(), base.key(), base.statusBefore(),
                base.statusAfter(), base.versionBefore(), base.versionAfter(), base.scopeVersion(),
                base.occurredAt(), null, text(command.reason()), null, null);
    }

    private LifecycleFacts deleteDraft(DeleteDraftFileCommand command, AtomicReference<LifecycleFacts> ignored) {
        ValidatedKey key = key(command.ownerContext(), command.objectType(), command.objectId(),
                command.purposeCode(), command.referenceKey());
        FileBusinessObjectPolicyFact policy = authorize(command.tenantId(), command.actorUserId(), key,
                FileActionCodes.DETACH);
        requireMutable(policy);
        FileArtifactDO artifact = artifactMapper.selectForUpdate(new FileArtifactLockQuery(
                command.tenantId(), command.artifactId()));
        if (artifact == null) throw exception(FILE_ARTIFACT_NOT_FOUND);
        if (!"DRAFT".equals(artifact.getLifecycleStatusCode())
                || !command.expectedArtifactVersion().equals(artifact.getVersion())
                || !referenceMapper.selectByArtifactForUpdate(
                new FileArtifactReferenceQuery(command.tenantId(), command.artifactId())).isEmpty()) {
            throw exception(FILE_COMMAND_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        if (artifactMapper.updateLifecycleIfMatch(new FileArtifactLifecycleUpdate(command.tenantId(),
                command.artifactId(), artifact.getVersion(), "DRAFT", "DRAFT", null, null,
                command.actorUserId(), now, true)) != 1) throw exception(FILE_FACT_VERSION_CONFLICT);
        return new LifecycleFacts(new LifecycleResult(command.artifactId(), null, null,
                artifact.getVersion() + 1, "DELETED"), command.tenantId(), command.actorUserId(),
                command.idempotencyKey(), command.artifactId(), null, null, key,
                "DRAFT", "DELETED", artifact.getVersion(), artifact.getVersion() + 1,
                policy.scopeVersion(), now, null, text(command.reason()));
    }

    private LifecycleFacts changeAvailability(ChangeFileAvailabilityCommand command,
                                              AtomicReference<LifecycleFacts> ignored) {
        ValidatedKey key = key(command.ownerContext(), command.objectType(), command.objectId(),
                command.purposeCode(), command.referenceKey());
        authorize(command.tenantId(), command.actorUserId(), key, FileActionCodes.INVALIDATE);
        FileArtifactDO artifact = artifactMapper.selectForUpdate(new FileArtifactLockQuery(
                command.tenantId(), command.artifactId()));
        if (artifact == null || !"ACTIVE".equals(artifact.getLifecycleStatusCode())) {
            throw exception(FILE_ARTIFACT_NOT_FOUND);
        }
        FileVersionDO version = versionMapper.selectForUpdate(new FileVersionLockQuery(
                command.tenantId(), command.artifactId(), command.versionNo()));
        if (version == null) throw exception(FILE_VERSION_NOT_FOUND);
        String target = text(command.targetStatus()).toUpperCase();
        String expected = version.getAvailabilityStatusCode();
        if (!("INVALIDATED".equals(target) && !"INVALIDATED".equals(expected))
                && !("AVAILABLE".equals(target) && "UNAVAILABLE".equals(expected))) {
            throw exception(FILE_COMMAND_INVALID);
        }
        if (!command.expectedAvailabilityVersion().equals(version.getAvailabilityVersion())) {
            throw exception(FILE_FACT_VERSION_CONFLICT);
        }
        LocalDateTime now = LocalDateTime.now();
        if (versionMapper.updateAvailabilityIfMatch(new FileVersionAvailabilityUpdate(command.tenantId(),
                command.artifactId(), command.versionNo(), version.getAvailabilityVersion(), expected,
                target, "AVAILABLE".equals(target) ? null : text(command.reasonCode()), now)) != 1) {
            throw exception(FILE_FACT_VERSION_CONFLICT);
        }
        return new LifecycleFacts(new LifecycleResult(command.artifactId(), command.versionNo(), null,
                version.getAvailabilityVersion() + 1, target), command.tenantId(), command.actorUserId(),
                command.idempotencyKey(), command.artifactId(), command.versionNo(), null, key,
                expected, target, version.getAvailabilityVersion(), version.getAvailabilityVersion() + 1,
                null, now, command.reasonCode(), command.reasonDetail());
    }

    private LifecycleFacts archive(ArchiveFileReferenceCommand command, AtomicReference<LifecycleFacts> ignored) {
        ValidatedKey key = key(command.ownerContext(), command.objectType(), command.objectId(),
                command.purposeCode(), command.referenceKey());
        FileBusinessObjectPolicyFact policy = authorize(command.tenantId(), command.actorUserId(), key,
                FileActionCodes.ARCHIVE);
        FileReferenceDO reference = lockReference(command.tenantId(), key);
        if (!command.referenceId().equals(reference.getId()) || !"ACTIVE".equals(reference.getStatusCode())
                || !command.expectedReferenceVersion().equals(reference.getVersion())) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
        FileArchiveRecordQuery archiveQuery = new FileArchiveRecordQuery(command.tenantId(),
                text(command.archiveBatchId()), reference.getArtifactId(), reference.getFileVersionNo());
        if (archiveRecordMapper.selectOne(archiveQuery) != null) throw exception(FILE_ARCHIVE_CONFLICT);
        LocalDateTime now = LocalDateTime.now();
        FileArchiveRecordDO record = new FileArchiveRecordDO();
        record.setTenantId(command.tenantId());
        record.setArtifactId(reference.getArtifactId());
        record.setFileVersionNo(reference.getFileVersionNo());
        record.setArchiveBatchId(archiveQuery.archiveBatchId());
        record.setBusinessDecisionRef(text(command.businessDecisionRef()));
        record.setArchivedBy(command.actorUserId());
        record.setArchivedAt(now);
        record.setArchiveNote(nullable(command.archiveNote()));
        record.setCreatedAt(now);
        if (archiveRecordMapper.insert(record) != 1 || referenceMapper.updateStateIfMatch(
                new FileReferenceStateUpdate(command.tenantId(), reference.getId(), reference.getVersion(),
                        "ACTIVE", "ARCHIVED", policy.scopeVersion(), command.actorUserId(), null, now)) != 1) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
        return facts(command.tenantId(), command.actorUserId(), command.idempotencyKey(), reference,
                "ACTIVE", "ARCHIVED", reference.getVersion(), reference.getVersion() + 1,
                policy.scopeVersion(), now, archiveQuery.archiveBatchId(), record.getBusinessDecisionRef());
    }

    private FileBusinessObjectPolicyFact authorize(Long tenantId, Long actorId, ValidatedKey key, String action) {
        FileBusinessObjectPolicyFact inspected = policyRegistry.inspect(new FileBusinessObjectPolicyQuery(
                tenantId, actorId, key.ownerContext(), key.objectType(), key.objectId(), key.purposeCode(),
                key.referenceKey(), action));
        return policyRegistry.lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(
                tenantId, actorId, key.ownerContext(), key.objectType(), key.objectId(), key.purposeCode(),
                key.referenceKey(), action, inspected.scopeVersion()));
    }

    private FileReferenceDO lockReference(Long tenantId, ValidatedKey key) {
        FileReferenceDO reference = referenceMapper.selectForUpdate(new FileReferenceLockQuery(tenantId,
                key.ownerContext(), key.objectType(), key.objectId(), key.purposeCode(), key.referenceKey()));
        if (reference == null) throw exception(FILE_REFERENCE_NOT_FOUND);
        return reference;
    }

    private PlatformCommandExecutionApi.SuccessFacts detachSuccess(DetachFileReferenceCommand command,
                                                                   LifecycleFacts facts) {
        var event = eventFactory.detached(new FileReferenceDetachedMessage(UUID.randomUUID().toString(),
                facts.tenantId(), facts.referenceId(), facts.artifactId(), facts.versionNo(),
                facts.key().ownerContext(), facts.key().objectType(), facts.key().objectId(),
                facts.key().purposeCode(), facts.occurredAt(), facts.operationId()));
        return success("FILE_REFERENCE_DETACH", facts, List.of(event));
    }

    private PlatformCommandExecutionApi.SuccessFacts archiveSuccess(ArchiveFileReferenceCommand command,
                                                                    LifecycleFacts facts) {
        var event = eventFactory.archived(new FileArchivedMessage(UUID.randomUUID().toString(),
                facts.tenantId(), facts.referenceId(), facts.artifactId(), facts.versionNo(),
                facts.key().ownerContext(), facts.key().objectType(), facts.key().objectId(),
                facts.key().purposeCode(), facts.archiveBatchId(), facts.businessDecisionRef(),
                facts.occurredAt(), facts.operationId()));
        return success("FILE_REFERENCE_ARCHIVE", facts, List.of(event));
    }

    private PlatformCommandExecutionApi.SuccessFacts genericSuccess(String operation, LifecycleFacts facts) {
        return success(operation, facts, List.of());
    }

    private PlatformCommandExecutionApi.SuccessFacts success(String operation, LifecycleFacts facts,
                                                             List<PlatformCommandExecutionApi.BusinessEvent> events) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("artifactId", value(facts.artifactId()));
        detail.put("versionNo", value(facts.versionNo()));
        detail.put("referenceId", value(facts.referenceId()));
        detail.put("statusBefore", facts.statusBefore());
        detail.put("statusAfter", facts.statusAfter());
        detail.put("versionBefore", facts.versionBefore());
        detail.put("versionAfter", facts.versionAfter());
        detail.put("scopeVersion", value(facts.scopeVersion()));
        detail.put("reasonCode", value(facts.reasonCode()));
        detail.put("reasonDetail", value(facts.reasonDetail()));
        detail.put("archiveBatchId", value(facts.archiveBatchId()));
        detail.put("businessDecisionRef", value(facts.businessDecisionRef()));
        detail.put("operationId", facts.operationId());
        return new PlatformCommandExecutionApi.SuccessFacts(operation, "FileArtifact",
                String.valueOf(facts.artifactId()), facts.operationId(), JsonUtils.toJsonString(detail), events);
    }

    private void auditRejected(Long tenantId, Long actorId, String operationId, String operation,
                               String resourceKey, RuntimeException failure) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operationId", operationId);
        detail.put("statusAfter", "REJECTED");
        detail.put("failureCode", failure instanceof ServiceException ex
                ? String.valueOf(ex.getCode()) : failure.getClass().getSimpleName());
        operationAuditApi.record(tenantId, actorId, operationId, operation, "FileArtifact",
                resourceKey, "REJECTED", Map.copyOf(detail));
    }

    private LifecycleFacts facts(Long tenantId, Long actorId, String operationId, FileReferenceDO reference,
                                 String before, String after, Integer versionBefore, Integer versionAfter,
                                 Long scopeVersion, LocalDateTime occurredAt,
                                 String archiveBatchId, String businessDecisionRef) {
        ValidatedKey key = new ValidatedKey(reference.getOwnerContext(), reference.getObjectType(),
                reference.getObjectId(), reference.getPurposeCode(), reference.getReferenceKey());
        return new LifecycleFacts(new LifecycleResult(reference.getArtifactId(), reference.getFileVersionNo(),
                reference.getId(), versionAfter, after), tenantId, actorId, operationId,
                reference.getArtifactId(), reference.getFileVersionNo(), reference.getId(), key,
                before, after, versionBefore, versionAfter, scopeVersion, occurredAt,
                null, null, archiveBatchId, businessDecisionRef);
    }

    private void validateActor(Long tenantId, Long actorId, String key) {
        if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0
                || key == null || key.isBlank() || key.length() > 128) throw exception(FILE_COMMAND_INVALID);
    }

    private ValidatedKey key(String ownerContext, String objectType, String objectId,
                             String purposeCode, String referenceKey) {
        try {
            return new ValidatedKey(FileActionCodes.requireText(ownerContext, "ownerContext"),
                    FileActionCodes.requireText(objectType, "objectType"),
                    FileActionCodes.requireText(objectId, "objectId"),
                    FileActionCodes.requireText(purposeCode, "purposeCode"),
                    FileActionCodes.requireText(referenceKey, "referenceKey"));
        } catch (IllegalArgumentException ex) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }

    private void requireMutable(FileBusinessObjectPolicyFact policy) {
        if (!"MUTABLE".equals(policy.referenceMutability())) throw exception(FILE_SCOPE_FORBIDDEN);
    }

    private String text(String value) {
        if (value == null || value.isBlank()) throw exception(FILE_COMMAND_INVALID);
        return value.trim();
    }

    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Object value(Object value) { return value == null ? "NONE" : value; }
    private String requestDigest(Object command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }
    private LifecycleFacts requireFacts(AtomicReference<LifecycleFacts> ref) {
        if (ref.get() == null) throw new IllegalStateException("FILE_LIFECYCLE_FACTS_MISSING");
        return ref.get();
    }

    @FunctionalInterface private interface Operation {
        LifecycleFacts run(AtomicReference<LifecycleFacts> facts);
    }
    @FunctionalInterface private interface Success {
        PlatformCommandExecutionApi.SuccessFacts create(LifecycleFacts facts);
    }

    public record LifecycleResult(Long artifactId, Integer versionNo, Long referenceId,
                                  Integer factVersion, String status) { }
    private record ValidatedKey(String ownerContext, String objectType, String objectId,
                                String purposeCode, String referenceKey) { }
    private record LifecycleFacts(LifecycleResult result, Long tenantId, Long actorUserId,
                                  String operationId, Long artifactId, Integer versionNo,
                                  Long referenceId, ValidatedKey key, String statusBefore,
                                  String statusAfter, Integer versionBefore, Integer versionAfter,
                                  Long scopeVersion, LocalDateTime occurredAt, String reasonCode,
                                  String reasonDetail, String archiveBatchId, String businessDecisionRef) {
        LifecycleFacts(LifecycleResult result, Long tenantId, Long actorUserId, String operationId,
                       Long artifactId, Integer versionNo, Long referenceId, ValidatedKey key,
                       String statusBefore, String statusAfter, Integer versionBefore, Integer versionAfter,
                       Long scopeVersion, LocalDateTime occurredAt, String reasonCode, String reasonDetail) {
            this(result, tenantId, actorUserId, operationId, artifactId, versionNo, referenceId, key,
                    statusBefore, statusAfter, versionBefore, versionAfter, scopeVersion, occurredAt,
                    reasonCode, reasonDetail, null, null);
        }
    }
}
