package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class AuthenticatedAssistedFileUploadService {
    private static final String OWNER = "ACC";
    private static final String OBJECT = "SATISFACTION_RESPONSE";
    private static final Set<String> POLICIES = Set.of("SATISFACTION_SIGNATURE", "SATISFACTION_ATTACHMENT");

    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final FileUploadApplicationService uploadService;
    private final FileUploadSessionMapper sessionMapper;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final OperationAuditApi operationAuditApi;
    private final PermissionApi permissionApi;

    @Transactional(rollbackFor = Exception.class)
    public AuthenticatedAssistedUploadInitialized initialize(
            Long actorUserId, AuthenticatedAssistedUploadInitializeCommand command) {
        requirePermissions(actorUserId);
        requireInitialize(command);
        policyRegistry.initializeAuthenticatedAssistedUploadPolicy(new AuthenticatedAssistedUploadInitializePolicyQuery(
                command.tenantId(), actorUserId, command.taskId(), command.questionnaireId(), command.requestId(),
                command.responseId(), command.policyKey(), "ALLOCATING:" + command.operationId(),
                "SATISFACTION_SIGNATURE".equals(command.policyKey()) ? 1 : 2));
        SlotIdentity slot = allocateSlot(command);
        AuthenticatedAssistedUploadPolicyFact policy = policyRegistry.initializeAuthenticatedAssistedUploadPolicy(
                new AuthenticatedAssistedUploadInitializePolicyQuery(command.tenantId(), actorUserId,
                        command.taskId(), command.questionnaireId(), command.requestId(), command.responseId(),
                        command.policyKey(), slot.fileSlotKey(), slot.fileSequence()));
        if (!command.policyKey().equals(command.categoryCode())) {
            throw new IllegalArgumentException("ASSISTED_FILE_CATEGORY_INVALID");
        }
        FileUploadInitialized initialized = uploadService.initializeAuthorized(new FileUploadInitializeCommand(
                command.tenantId(), actorUserId, command.operationId(), FileUploadApplicationService.MODE_CREATE_ARTIFACT,
                null, null, OWNER, OBJECT, String.valueOf(command.responseId()), command.policyKey(),
                slot.fileSlotKey(), command.fileName(), command.categoryCode(), command.declaredSizeBytes(),
                command.declaredMediaType(), command.clientSha256()), policy.filePolicy());
        audit(command.tenantId(), actorUserId, command.operationId(), "INITIALIZED", command.taskId(),
                command.questionnaireId(), command.requestId(), command.responseId(), command.policyKey(),
                slot.fileSlotKey(), slot.fileSequence(), initialized.artifactId());
        return new AuthenticatedAssistedUploadInitialized(command.responseId(), slot.fileSlotKey(),
                slot.fileSequence(), initialized.artifactId(), initialized.sessionId(), policy.scopeVersion(),
                initialized.expiresAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticatedAssistedFileFact complete(
            Long actorUserId, AuthenticatedAssistedUploadCompleteCommand command) {
        requirePermissions(actorUserId);
        requireComplete(command);
        FileUploadSessionDO session = sessionMapper.selectForUpdate(
                new FileUploadSessionLockQuery(command.tenantId(), command.sessionId()));
        SlotIdentity slot = requireSession(command, session);
        AuthenticatedAssistedUploadPolicyFact policy = policyRegistry.lockAndRevalidateAuthenticatedAssistedUpload(
                new AuthenticatedAssistedUploadCompletePolicyQuery(command.tenantId(), actorUserId,
                        command.taskId(), command.questionnaireId(), command.requestId(), command.responseId(),
                        command.policyKey(), slot.fileSlotKey(), slot.fileSequence(), session.getScopeVersion()));
        FileUploadCompleted completed = uploadService.completeAuthorized(new FileUploadCompleteCommand(
                command.tenantId(), actorUserId, slot.operationId(), command.artifactId(), command.sessionId(),
                null, command.clientSha256()), command.content(), policy.filePolicy());
        AuthenticatedAssistedFileFact fact = fact(command.tenantId(), command.responseId(), policy.scopeVersion(),
                command.policyKey(), slot.fileSlotKey(), slot.fileSequence(), completed.artifactId(),
                completed.versionNo(), completed.referenceKey());
        audit(command.tenantId(), actorUserId, slot.operationId(), "COMPLETED", command.taskId(),
                command.questionnaireId(), command.requestId(), command.responseId(), command.policyKey(),
                slot.fileSlotKey(), slot.fileSequence(), completed.artifactId());
        return fact;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<AuthenticatedAssistedFileFact> lockAndRevalidate(
            Long actorUserId, AuthenticatedAssistedFilesRevalidationCommand command) {
        requirePermissions(actorUserId);
        requireRevalidation(command);
        policyRegistry.lockAndRevalidateAuthenticatedAssistedFiles(new AuthenticatedAssistedFileRevalidationQuery(
                command.tenantId(), actorUserId, command.taskId(), command.questionnaireId(), command.requestId(),
                command.responseId(), command.files()));
        List<AuthenticatedAssistedFileFact> facts = new ArrayList<>(command.files().size());
        for (AuthenticatedAssistedFileHandle handle : command.files().stream()
                .sorted(Comparator.comparing(AuthenticatedAssistedFileHandle::policyKey)
                        .thenComparing(AuthenticatedAssistedFileHandle::fileSequence)).toList()) {
            AuthenticatedAssistedFileFact actual = fact(command.tenantId(), command.responseId(), handle.scopeVersion(),
                    handle.policyKey(), handle.fileSlotKey(), handle.fileSequence(), handle.artifactId(),
                    handle.versionNo(), handle.referenceKey());
            FileArtifactVersionFact file = actual.fileFact();
            if (!Objects.equals(handle.artifactVersion(), file.fileFactVersion().artifactVersion())
                    || !Objects.equals(handle.referenceVersion(), file.fileFactVersion().referenceVersion())
                    || !Objects.equals(handle.availabilityVersion(), file.fileFactVersion().availabilityVersion())
                    || !Objects.equals(handle.sha256(), file.sha256())) {
                throw new IllegalStateException("ASSISTED_FILE_FACT_CONFLICT");
            }
            facts.add(actual);
        }
        return List.copyOf(facts);
    }

    private AuthenticatedAssistedFileFact fact(Long tenantId, Long responseId, Long scopeVersion,
                                                String policyKey, String fileSlotKey, Integer sequence,
                                                Long artifactId, Integer versionNo, String referenceKey) {
        SlotIdentity slot = slotIdentity(referenceKey);
        if (!Objects.equals(responseId, slot.responseId()) || !Objects.equals(fileSlotKey, referenceKey)
                || !Objects.equals(sequence, slot.fileSequence())) {
            throw new IllegalStateException("ASSISTED_FILE_REFERENCE_INVALID");
        }
        FileReferenceDO reference = referenceMapper.selectForUpdate(new FileReferenceLockQuery(
                tenantId, OWNER, OBJECT, String.valueOf(responseId), policyKey, referenceKey));
        FileArtifactDO artifact = artifactMapper.selectForUpdate(new FileArtifactLockQuery(tenantId, artifactId));
        FileVersionDO version = versionMapper.selectForUpdate(new FileVersionLockQuery(tenantId, artifactId, versionNo));
        if (reference == null || artifact == null || version == null
                || !Objects.equals(artifactId, reference.getArtifactId())
                || !Objects.equals(versionNo, reference.getFileVersionNo())
                || !Objects.equals(scopeVersion, reference.getScopeVersion())
                || !OWNER.equals(artifact.getOwnerContext()) || !policyKey.equals(artifact.getCategoryCode())
                || !"ACTIVE".equals(artifact.getLifecycleStatusCode())
                || !"ACTIVE".equals(reference.getStatusCode())
                || !"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            throw new IllegalStateException("ASSISTED_FILE_FACT_CONFLICT");
        }
        return new AuthenticatedAssistedFileFact(policyKey, fileSlotKey, sequence,
                new FileArtifactVersionFact(artifactId, versionNo, referenceKey, artifact.getCategoryCode(),
                        artifact.getName(), version.getSizeBytes(), version.getDetectedMediaType(), version.getSha256(),
                        version.getAvailabilityStatusCode(), reference.getStatusCode(),
                        new FileFactVersion(artifact.getVersion(), reference.getVersion(),
                                version.getAvailabilityVersion()), scopeVersion));
    }

    private SlotIdentity allocateSlot(AuthenticatedAssistedUploadInitializeCommand command) {
        List<FileUploadSessionDO> sessions = sessionMapper.selectBusinessGrantSlotsForUpdate(
                new BusinessGrantUploadSessionQuery(command.tenantId(), OWNER, OBJECT,
                        String.valueOf(command.responseId())));
        for (FileUploadSessionDO session : sessions) {
            SlotIdentity existing = slotIdentity(session.getReferenceKey());
            if (command.policyKey().equals(session.getPurposeCode())
                    && command.operationId().trim().equals(existing.operationId())) return existing;
        }
        if ("SATISFACTION_SIGNATURE".equals(command.policyKey())) {
            if (sessions.stream().anyMatch(s -> "SATISFACTION_SIGNATURE".equals(s.getPurposeCode()))) {
                throw new IllegalStateException("ASSISTED_SIGNATURE_SLOT_OCCUPIED");
            }
            return slot(command, 1);
        }
        int next = sessions.stream().filter(s -> "SATISFACTION_ATTACHMENT".equals(s.getPurposeCode()))
                .mapToInt(s -> slotIdentity(s.getReferenceKey()).fileSequence()).max().orElse(1) + 1;
        return slot(command, next);
    }

    private SlotIdentity requireSession(AuthenticatedAssistedUploadCompleteCommand command, FileUploadSessionDO session) {
        if (session == null || !Objects.equals(command.artifactId(), session.getArtifactId())
                || !OWNER.equals(session.getOwnerContext()) || !OBJECT.equals(session.getObjectType())
                || !String.valueOf(command.responseId()).equals(session.getObjectId())
                || !Objects.equals(command.policyKey(), session.getPurposeCode())
                || !Objects.equals(command.fileSlotKey(), session.getReferenceKey())) {
            throw new IllegalStateException("ASSISTED_FILE_SESSION_CONFLICT");
        }
        SlotIdentity slot = slotIdentity(session.getReferenceKey());
        if (!Objects.equals(command.responseId(), slot.responseId())
                || !Objects.equals(command.fileSequence(), slot.fileSequence())
                || !command.operationId().trim().equals(slot.operationId())) {
            throw new IllegalStateException("ASSISTED_FILE_SESSION_CONFLICT");
        }
        return slot;
    }

    private SlotIdentity slot(AuthenticatedAssistedUploadInitializeCommand command, int sequence) {
        String operationId = command.operationId().trim();
        String fileSlotKey = "af:" + command.responseId() + ":" + sequence + ":" + operationId;
        if (fileSlotKey.length() > 64) throw new IllegalArgumentException("ASSISTED_FILE_SLOT_TOO_LONG");
        return new SlotIdentity(fileSlotKey, command.responseId(), sequence, operationId);
    }

    private SlotIdentity slotIdentity(String key) {
        String[] parts = key == null ? new String[0] : key.split(":", 4);
        if (parts.length != 4 || !"af".equals(parts[0]) || blank(parts[3])) {
            throw new IllegalStateException("ASSISTED_FILE_REFERENCE_INVALID");
        }
        try {
            long responseId = Long.parseLong(parts[1]);
            int sequence = Integer.parseInt(parts[2]);
            if (responseId <= 0 || sequence <= 0) throw new NumberFormatException();
            return new SlotIdentity(key, responseId, sequence, parts[3]);
        } catch (NumberFormatException ex) { throw new IllegalStateException("ASSISTED_FILE_REFERENCE_INVALID"); }
    }

    private void requirePermissions(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0
                || !permissionApi.hasAnyPermissions(actorUserId, "pms:acceptance:satisfaction:collect")
                || !permissionApi.hasAnyPermissions(actorUserId, "pms:file:upload")) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
    }

    private void requireInitialize(AuthenticatedAssistedUploadInitializeCommand c) {
        if (c == null || c.tenantId() == null || c.taskId() == null || c.questionnaireId() == null
                || c.responseId() == null || blank(c.requestId()) || !POLICIES.contains(c.policyKey())
                || blank(c.operationId()) || c.operationId().length() > 32 || blank(c.fileName())
                || !Objects.equals(c.policyKey(), c.categoryCode()) || c.declaredSizeBytes() == null
                || c.declaredSizeBytes() <= 0 || blank(c.declaredMediaType())) {
            throw new IllegalArgumentException("ASSISTED_UPLOAD_INITIALIZE_INVALID");
        }
    }

    private void requireComplete(AuthenticatedAssistedUploadCompleteCommand c) {
        if (c == null || c.content() == null || c.content().length == 0 || blank(c.requestId())
                || !POLICIES.contains(c.policyKey()) || blank(c.operationId()) || blank(c.fileSlotKey())
                || c.fileSequence() == null || c.fileSequence() <= 0 || c.artifactId() == null
                || c.sessionId() == null) throw new IllegalArgumentException("ASSISTED_UPLOAD_COMPLETE_INVALID");
    }

    private void requireRevalidation(AuthenticatedAssistedFilesRevalidationCommand c) {
        if (c == null || c.files().isEmpty() || blank(c.requestId()) || c.responseId() == null
                || c.files().stream().anyMatch(h -> h == null || !POLICIES.contains(h.policyKey())
                || blank(h.fileSlotKey()) || h.fileSequence() == null || h.fileSequence() <= 0
                || h.artifactId() == null || h.versionNo() == null || blank(h.referenceKey())
                || h.artifactVersion() == null || h.referenceVersion() == null
                || h.availabilityVersion() == null || h.scopeVersion() == null || blank(h.sha256()))) {
            throw new IllegalArgumentException("ASSISTED_FILE_REVALIDATION_INVALID");
        }
    }

    private void audit(Long tenantId, Long actorId, String operationId, String status, Long taskId,
                       Long questionnaireId, String requestId, Long responseId, String policyKey,
                       String fileSlotKey, Integer sequence, Long artifactId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("subjectType", "AUTHENTICATED_ASSISTED"); detail.put("taskId", taskId);
        detail.put("questionnaireId", questionnaireId); detail.put("requestId", requestId);
        detail.put("responseId", responseId); detail.put("policyKey", policyKey);
        detail.put("fileSlotKey", fileSlotKey); detail.put("fileSequence", sequence);
        detail.put("artifactId", artifactId);
        operationAuditApi.record(tenantId, actorId, operationId, "ASSISTED_FILE_UPLOAD",
                "SatisfactionResponse", String.valueOf(responseId), status, Map.copyOf(detail));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private record SlotIdentity(String fileSlotKey, Long responseId, Integer fileSequence, String operationId) {}
}
