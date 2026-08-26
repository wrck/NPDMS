package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyRevalidationQuery;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageStoreCommand;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactActivationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileArtifactLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileReferenceReplaceVersionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionCompletionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionLockQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileUploadSessionValidationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionCursorQuery;
import cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleteCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadCompleted;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import cn.iocoder.yudao.module.pms.platform.service.file.event.FileEventFactory;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SIZE_EXCEEDED;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_ARTIFACT_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_DIGEST_MISMATCH;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_REFERENCE_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_REFERENCE_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_STORAGE_RECEIPT_CONFLICT;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_UPLOAD_SESSION_EXPIRED;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_UPLOAD_SESSION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_UPLOAD_SESSION_STATE_INVALID;

@Service
public class FileUploadApplicationService {

    static final String MODE_CREATE_ARTIFACT = "CREATE_ARTIFACT";
    static final String MODE_ADD_VERSION = "ADD_VERSION";
    static final long PLATFORM_MAX_BYTES = 52_428_800L;

    private final FileUploadSessionMapper sessionMapper;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final BoundedMultipartReader multipartReader;
    private final FileContentPolicyService contentPolicyService;
    private final FileStorageReceiptApi storageReceiptApi;
    private final FileEventFactory eventFactory;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final Duration sessionTtl;

    public FileUploadApplicationService(
            FileUploadSessionMapper sessionMapper,
            FileArtifactMapper artifactMapper,
            FileVersionMapper versionMapper,
            FileReferenceMapper referenceMapper,
            FileBusinessObjectPolicyRegistry policyRegistry,
            BoundedMultipartReader multipartReader,
            FileContentPolicyService contentPolicyService,
            FileStorageReceiptApi storageReceiptApi,
            FileEventFactory eventFactory,
            PlatformCommandExecutionApi commandExecutionApi,
            OperationAuditApi operationAuditApi,
            @Value("${pms.file.upload.session-ttl:PT15M}") Duration sessionTtl) {
        this.sessionMapper = sessionMapper;
        this.artifactMapper = artifactMapper;
        this.versionMapper = versionMapper;
        this.referenceMapper = referenceMapper;
        this.policyRegistry = policyRegistry;
        this.multipartReader = multipartReader;
        this.contentPolicyService = contentPolicyService;
        this.storageReceiptApi = storageReceiptApi;
        this.eventFactory = eventFactory;
        this.commandExecutionApi = commandExecutionApi;
        this.operationAuditApi = operationAuditApi;
        this.sessionTtl = sessionTtl;
    }

    public FileUploadCompleted complete(FileUploadCompleteCommand command) {
        try {
            validateComplete(command);
            byte[] content = multipartReader.read(command.file(), PLATFORM_MAX_BYTES);
            String actualSha256 = contentSha256(content);
            AtomicReference<CompletionFacts> completionRef = new AtomicReference<>();
            var execution = commandExecutionApi.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                            "PLT:FILE:COMPLETE_UPLOAD", command.actorUserId(), command.idempotencyKey()),
                    completionRequestDigest(command, actualSha256), FileUploadCompleted.class,
                    () -> completeOnce(command, content, completionRef),
                    completed -> completionSuccessFacts(completed, requireCompletion(completionRef)));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                    || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                throw exception(FILE_COMMAND_INVALID);
            }
            return execution.response();
        } catch (RuntimeException failure) {
            auditCompleteRejected(command, failure);
            throw failure;
        }
    }

    private FileUploadCompleted completeOnce(FileUploadCompleteCommand command, byte[] content,
                                              AtomicReference<CompletionFacts> completionRef) {
        FileUploadSessionDO session = sessionMapper.selectForUpdate(
                new FileUploadSessionLockQuery(command.tenantId(), command.sessionId()));
        requireCompletableSession(command, session);
        if (sessionMapper.beginValidationIfInitialized(new FileUploadSessionValidationUpdate(
                command.tenantId(), session.getId(), session.getVersion())) != 1) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        String action = MODE_CREATE_ARTIFACT.equals(session.getModeCode())
                ? FileActionCodes.UPLOAD : FileActionCodes.REPLACE;
        FileBusinessObjectPolicyFact policy = policyRegistry.lockAndRevalidate(
                new FileBusinessObjectPolicyRevalidationQuery(command.tenantId(), command.actorUserId(),
                        session.getOwnerContext(), session.getObjectType(), session.getObjectId(),
                        session.getPurposeCode(), session.getReferenceKey(), action, session.getScopeVersion()));
        validatePolicy(new ValidatedInitialization(session.getModeCode(), command.idempotencyKey(),
                        session.getOwnerContext(), session.getObjectType(), session.getObjectId(),
                        session.getPurposeCode(), session.getReferenceKey(), session.getFileName(),
                        session.getCategoryCode(), session.getDeclaredMediaType(), action),
                session.getDeclaredSizeBytes(), policy);
        String clientSha256 = effectiveClientDigest(session.getClientSha256(), command.clientSha256());
        ValidatedFileContent validated = contentPolicyService.validateBounded(
                new BoundedFileContentValidationCommand(content, session.getFileName(),
                        session.getDeclaredSizeBytes(), session.getDeclaredMediaType(), clientSha256, policy));
        FileStorageReceipt receipt = storageReceiptApi.store(new FileStorageStoreCommand(
                session.getStorageOperationId(), validated.content(), session.getFileName(),
                validated.mediaType()));
        requireMatchingReceipt(session, validated, receipt);
        CompletionFacts facts = persistCompleted(command, session, policy, validated, receipt);
        completionRef.set(facts);
        return new FileUploadCompleted(facts.artifactId(), facts.versionNo(), facts.referenceId(),
                session.getReferenceKey(), validated.sha256());
    }

    private CompletionFacts persistCompleted(FileUploadCompleteCommand command, FileUploadSessionDO session,
                                               FileBusinessObjectPolicyFact policy,
                                               ValidatedFileContent content, FileStorageReceipt receipt) {
        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact;
        FileReferenceDO reference;
        int versionNo;
        if (MODE_CREATE_ARTIFACT.equals(session.getModeCode())) {
            if (artifactMapper.selectForUpdate(new FileArtifactLockQuery(
                    command.tenantId(), session.getArtifactId())) != null) {
                throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
            }
            reference = referenceMapper.selectForUpdate(exactReference(command.tenantId(), session));
            if (reference != null) {
                throw exception(FILE_REFERENCE_VERSION_CONFLICT);
            }
            artifact = newArtifact(command, session);
            if (artifactMapper.insert(artifact) != 1) {
                throw new IllegalStateException("FILE_ARTIFACT_CREATE_FAILED");
            }
            versionNo = 1;
        } else {
            artifact = artifactMapper.selectForUpdate(new FileArtifactLockQuery(
                    command.tenantId(), session.getArtifactId()));
            if (artifact == null) {
                throw exception(FILE_ARTIFACT_NOT_FOUND);
            }
            if (!"ACTIVE".equals(artifact.getLifecycleStatusCode())) {
                throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
            }
            reference = referenceMapper.selectForUpdate(exactReference(command.tenantId(), session));
            if (reference == null) {
                throw exception(FILE_REFERENCE_NOT_FOUND);
            }
            if (!session.getArtifactId().equals(reference.getArtifactId())
                    || !"ACTIVE".equals(reference.getStatusCode())
                    || !session.getExpectedReferenceVersion().equals(reference.getVersion())) {
                throw exception(FILE_REFERENCE_VERSION_CONFLICT);
            }
            List<FileVersionDO> latest = versionMapper.selectCursor(
                    new FileVersionCursorQuery(command.tenantId(), artifact.getId(), null, null, 1));
            versionNo = latest.isEmpty() ? 1 : latest.getFirst().getVersionNo() + 1;
        }

        FileVersionDO version = newVersion(command, session, content, receipt, versionNo, now);
        if (versionMapper.insert(version) != 1) {
            throw new IllegalStateException("FILE_VERSION_CREATE_FAILED");
        }
        if (MODE_CREATE_ARTIFACT.equals(session.getModeCode())) {
            reference = newReference(command, session, policy, versionNo);
            if (referenceMapper.insert(reference) != 1
                    || artifactMapper.activateDraftIfMatch(new FileArtifactActivationUpdate(
                    command.tenantId(), artifact.getId(), 0)) != 1) {
                throw new IllegalStateException("FILE_REFERENCE_CREATE_FAILED");
            }
        } else if (referenceMapper.replaceVersionIfMatch(new FileReferenceReplaceVersionUpdate(
                command.tenantId(), reference.getId(), session.getExpectedReferenceVersion(),
                artifact.getId(), versionNo, policy.scopeVersion(), policy.sensitivityCode())) != 1) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
        if (sessionMapper.completeIfValidating(new FileUploadSessionCompletionUpdate(
                command.tenantId(), session.getId(), session.getVersion() + 1, artifact.getId(),
                reference.getId(), content.sha256(), versionNo, receipt.infraFileId(), now)) != 1) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        return new CompletionFacts(artifact.getId(), versionNo, reference.getId(),
                session, policy, content, receipt, now);
    }

    public FileUploadInitialized initialize(FileUploadInitializeCommand command) {
        try {
            return initializeInTransaction(command);
        } catch (RuntimeException failure) {
            auditRejected(command, failure);
            throw failure;
        }
    }

    private FileUploadInitialized initializeInTransaction(FileUploadInitializeCommand command) {
        ValidatedInitialization validated = validate(command);
        Long artifactId = MODE_CREATE_ARTIFACT.equals(validated.modeCode())
                ? IdWorker.getId() : command.artifactId();
        Long sessionId = IdWorker.getId();
        LocalDateTime expiresAt = LocalDateTime.now().plus(sessionTtl);
        AtomicReference<FileBusinessObjectPolicyFact> policyRef = new AtomicReference<>();
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(command.tenantId(),
                        "PLT:FILE:INIT_UPLOAD", command.actorUserId(), validated.idempotencyKey()),
                requestDigest(command, validated), FileUploadInitialized.class,
                () -> authorizeAndCreateSession(command, validated, artifactId, sessionId, expiresAt, policyRef),
                initialized -> successFacts(initialized, command, validated, requirePolicy(policyRef)));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                || execution.response() == null) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return execution.response();
    }

    private FileUploadInitialized authorizeAndCreateSession(
            FileUploadInitializeCommand command, ValidatedInitialization validated,
            Long artifactId, Long sessionId, LocalDateTime expiresAt,
            AtomicReference<FileBusinessObjectPolicyFact> policyRef) {
        FileBusinessObjectPolicyFact policy = policyRegistry.inspect(new FileBusinessObjectPolicyQuery(
                command.tenantId(), command.actorUserId(), validated.ownerContext(), validated.objectType(),
                validated.objectId(), validated.purposeCode(), validated.referenceKey(), validated.action()));
        validatePolicy(validated, command.declaredSizeBytes(), policy);
        policyRef.set(policy);
        return createSession(command, validated, policy, artifactId, sessionId, expiresAt);
    }

    private FileUploadInitialized createSession(
            FileUploadInitializeCommand command, ValidatedInitialization validated,
            FileBusinessObjectPolicyFact policy, Long artifactId, Long sessionId,
            LocalDateTime expiresAt) {
        FileUploadSessionDO row = new FileUploadSessionDO();
        row.setId(sessionId);
        row.setModeCode(validated.modeCode());
        row.setOwnerContext(validated.ownerContext());
        row.setObjectType(validated.objectType());
        row.setObjectId(validated.objectId());
        row.setPurposeCode(validated.purposeCode());
        row.setReferenceKey(validated.referenceKey());
        row.setFileName(validated.fileName());
        row.setCategoryCode(validated.categoryCode());
        row.setDeclaredSizeBytes(command.declaredSizeBytes());
        row.setDeclaredMediaType(validated.declaredMediaType());
        row.setStorageOperationId(String.valueOf(sessionId));
        row.setStatusCode("INITIALIZED");
        row.setScopeVersion(policy.scopeVersion());
        row.setExpiresAt(expiresAt);
        row.setVersion(0);
        row.setArtifactId(artifactId);
        row.setExpectedReferenceVersion(command.expectedReferenceVersion());
        row.setClientSha256(normalizeDigest(command.clientSha256()));
        row.setCreator(String.valueOf(command.actorUserId()));
        row.setUpdater(String.valueOf(command.actorUserId()));
        row.setTenantId(command.tenantId());
        if (sessionMapper.insert(row) != 1) {
            throw new IllegalStateException("FILE_UPLOAD_SESSION_CREATE_FAILED");
        }
        return new FileUploadInitialized(artifactId, sessionId, expiresAt);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            FileUploadInitialized initialized, FileUploadInitializeCommand command,
            ValidatedInitialization validated,
            FileBusinessObjectPolicyFact policy) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("artifactId", initialized.artifactId());
        detail.put("sessionId", initialized.sessionId());
        detail.put("modeCode", validated.modeCode());
        detail.put("ownerContext", validated.ownerContext());
        detail.put("objectType", validated.objectType());
        detail.put("objectId", validated.objectId());
        detail.put("purposeCode", validated.purposeCode());
        detail.put("referenceKey", validated.referenceKey());
        detail.put("fileName", validated.fileName());
        detail.put("categoryCode", validated.categoryCode());
        detail.put("declaredSizeBytes", command.declaredSizeBytes());
        detail.put("declaredMediaType", validated.declaredMediaType());
        detail.put("clientSha256", auditValue(normalizeDigest(command.clientSha256())));
        detail.put("expectedReferenceVersion", auditValue(command.expectedReferenceVersion()));
        detail.put("action", validated.action());
        detail.put("scopeVersion", policy.scopeVersion());
        detail.put("operationId", validated.idempotencyKey());
        detail.put("storageOperationId", String.valueOf(initialized.sessionId()));
        detail.put("statusBefore", "NONE");
        detail.put("statusAfter", "INITIALIZED");
        detail.put("versionBefore", "NONE");
        detail.put("versionAfter", 0);
        detail.put("expiresAt", initialized.expiresAt());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "FILE_UPLOAD_INITIALIZE", "FileUploadSession", String.valueOf(initialized.sessionId()),
                validated.idempotencyKey(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRejected(FileUploadInitializeCommand command, RuntimeException failure) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        putIfNotNull(detail, "modeCode", auditText(command.modeCode()));
        putIfNotNull(detail, "artifactId", command.artifactId());
        putIfNotNull(detail, "ownerContext", auditText(command.ownerContext()));
        putIfNotNull(detail, "objectType", auditText(command.objectType()));
        putIfNotNull(detail, "objectId", auditText(command.objectId()));
        putIfNotNull(detail, "purposeCode", auditText(command.purposeCode()));
        putIfNotNull(detail, "referenceKey", auditText(command.referenceKey()));
        putIfNotNull(detail, "fileName", auditText(command.fileName()));
        putIfNotNull(detail, "categoryCode", auditText(command.categoryCode()));
        putIfNotNull(detail, "declaredSizeBytes", command.declaredSizeBytes());
        putIfNotNull(detail, "declaredMediaType", auditText(command.declaredMediaType()));
        putIfNotNull(detail, "clientSha256", auditText(command.clientSha256()));
        detail.put("action", MODE_ADD_VERSION.equals(auditText(command.modeCode()))
                ? FileActionCodes.REPLACE : FileActionCodes.UPLOAD);
        detail.put("operationId", command.idempotencyKey());
        detail.put("expectedReferenceVersion", auditValue(command.expectedReferenceVersion()));
        detail.put("statusBefore", "NONE");
        detail.put("statusAfter", "REJECTED");
        detail.put("versionBefore", "NONE");
        detail.put("versionAfter", "NONE");
        detail.put("failureCode", failureCode(failure));
        operationAuditApi.record(command.tenantId(), command.actorUserId(), command.idempotencyKey(),
                "FILE_UPLOAD_INITIALIZE", "FileUploadSession", "UNKNOWN", "REJECTED", Map.copyOf(detail));
    }

    private PlatformCommandExecutionApi.SuccessFacts completionSuccessFacts(
            FileUploadCompleted completed, CompletionFacts facts) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("artifactId", completed.artifactId());
        detail.put("versionNo", completed.versionNo());
        detail.put("referenceId", completed.referenceId());
        detail.put("sessionId", facts.session().getId());
        detail.put("ownerContext", facts.session().getOwnerContext());
        detail.put("objectType", facts.session().getObjectType());
        detail.put("objectId", facts.session().getObjectId());
        detail.put("purposeCode", facts.session().getPurposeCode());
        detail.put("referenceKey", facts.session().getReferenceKey());
        detail.put("action", MODE_CREATE_ARTIFACT.equals(facts.session().getModeCode())
                ? FileActionCodes.UPLOAD : FileActionCodes.REPLACE);
        detail.put("fileName", facts.session().getFileName());
        detail.put("sizeBytes", facts.content().sizeBytes());
        detail.put("mediaType", facts.content().mediaType());
        detail.put("sha256", facts.content().sha256());
        detail.put("scanStatus", "PASSED");
        detail.put("scanProviderCode", facts.content().scanProviderCode());
        detail.put("scanProviderVersion", auditValue(facts.content().scanProviderVersion()));
        detail.put("scopeVersion", facts.policy().scopeVersion());
        detail.put("operationId", facts.session().getStorageOperationId());
        detail.put("sessionStatusBefore", "INITIALIZED");
        detail.put("sessionStatusAfter", "COMPLETED");
        detail.put("sessionVersionBefore", facts.session().getVersion());
        detail.put("sessionVersionAfter", facts.session().getVersion() + 2);
        detail.put("artifactVersionAfter", MODE_CREATE_ARTIFACT.equals(facts.session().getModeCode()) ? 1 : "UNCHANGED");
        detail.put("referenceVersionAfter", MODE_CREATE_ARTIFACT.equals(facts.session().getModeCode())
                ? 0 : facts.session().getExpectedReferenceVersion() + 1);
        var versionEvent = eventFactory.versionCommitted(facts.session().getTenantId(), completed.artifactId(),
                completed.versionNo(), completed.sha256(), facts.occurredAt(),
                facts.session().getStorageOperationId());
        var referenceEvent = eventFactory.referenceAttached(facts.session().getTenantId(), completed.referenceId(),
                completed.artifactId(), completed.versionNo(), facts.session().getOwnerContext(),
                facts.session().getObjectType(), facts.session().getObjectId(), facts.session().getPurposeCode(),
                facts.occurredAt(), facts.session().getStorageOperationId());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "FILE_UPLOAD_COMPLETE", "FileArtifact", String.valueOf(completed.artifactId()),
                facts.session().getStorageOperationId(), JsonUtils.toJsonString(detail),
                List.of(versionEvent, referenceEvent));
    }

    private void auditCompleteRejected(FileUploadCompleteCommand command, RuntimeException failure) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        putIfNotNull(detail, "artifactId", command.artifactId());
        putIfNotNull(detail, "sessionId", command.sessionId());
        detail.put("operationId", command.idempotencyKey());
        detail.put("statusAfter", "REJECTED");
        detail.put("failureCode", failureCode(failure));
        operationAuditApi.record(command.tenantId(), command.actorUserId(), command.idempotencyKey(),
                "FILE_UPLOAD_COMPLETE", "FileArtifact",
                command.artifactId() == null ? "UNKNOWN" : String.valueOf(command.artifactId()),
                "REJECTED", Map.copyOf(detail));
    }

    private void validateComplete(FileUploadCompleteCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.artifactId() == null || command.artifactId() <= 0
                || command.sessionId() == null || command.sessionId() <= 0
                || command.file() == null || command.file().isEmpty()) {
            throw exception(FILE_COMMAND_INVALID);
        }
        limitedText(command.idempotencyKey(), 128);
        normalizeDigest(command.clientSha256());
    }

    private void requireCompletableSession(FileUploadCompleteCommand command, FileUploadSessionDO session) {
        if (session == null) {
            throw exception(FILE_UPLOAD_SESSION_NOT_FOUND);
        }
        if (!command.artifactId().equals(session.getArtifactId())
                || !"INITIALIZED".equals(session.getStatusCode())) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw exception(FILE_UPLOAD_SESSION_EXPIRED);
        }
    }

    private FileArtifactDO newArtifact(FileUploadCompleteCommand command, FileUploadSessionDO session) {
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setId(session.getArtifactId());
        artifact.setName(session.getFileName());
        artifact.setCategoryCode(session.getCategoryCode());
        artifact.setOwnerContext(session.getOwnerContext());
        artifact.setLifecycleStatusCode("DRAFT");
        artifact.setVersion(0);
        artifact.setCreator(String.valueOf(command.actorUserId()));
        artifact.setUpdater(String.valueOf(command.actorUserId()));
        artifact.setTenantId(command.tenantId());
        return artifact;
    }

    private FileVersionDO newVersion(FileUploadCompleteCommand command, FileUploadSessionDO session,
                                     ValidatedFileContent content, FileStorageReceipt receipt,
                                     int versionNo, LocalDateTime now) {
        FileVersionDO version = new FileVersionDO();
        version.setArtifactId(session.getArtifactId());
        version.setVersionNo(versionNo);
        version.setInfraFileId(receipt.infraFileId());
        version.setAvailabilityVersion(0);
        version.setSha256(content.sha256());
        version.setSizeBytes(content.sizeBytes());
        version.setDeclaredMediaType(session.getDeclaredMediaType());
        version.setDetectedMediaType(content.mediaType());
        version.setScanStatusCode("PASSED");
        version.setScanProviderCode(content.scanProviderCode());
        version.setScanProviderVersion(content.scanProviderVersion());
        version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(command.actorUserId());
        version.setCreatedAt(now);
        version.setTenantId(command.tenantId());
        return version;
    }

    private FileReferenceDO newReference(FileUploadCompleteCommand command, FileUploadSessionDO session,
                                         FileBusinessObjectPolicyFact policy, int versionNo) {
        FileReferenceDO reference = new FileReferenceDO();
        reference.setOwnerContext(session.getOwnerContext());
        reference.setObjectType(session.getObjectType());
        reference.setObjectId(session.getObjectId());
        reference.setPurposeCode(session.getPurposeCode());
        reference.setReferenceKey(session.getReferenceKey());
        reference.setArtifactId(session.getArtifactId());
        reference.setFileVersionNo(versionNo);
        reference.setSensitivityCode(policy.sensitivityCode());
        reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(policy.scopeVersion());
        reference.setVersion(0);
        reference.setCreator(String.valueOf(command.actorUserId()));
        reference.setUpdater(String.valueOf(command.actorUserId()));
        reference.setTenantId(command.tenantId());
        return reference;
    }

    private FileReferenceLockQuery exactReference(Long tenantId, FileUploadSessionDO session) {
        return new FileReferenceLockQuery(tenantId, session.getOwnerContext(), session.getObjectType(),
                session.getObjectId(), session.getPurposeCode(), session.getReferenceKey());
    }

    private void requireMatchingReceipt(FileUploadSessionDO session, ValidatedFileContent content,
                                        FileStorageReceipt receipt) {
        if (receipt == null || receipt.infraFileId() == null || receipt.infraFileId() <= 0
                || !session.getStorageOperationId().equals(receipt.storageOperationId())
                || !session.getFileName().equals(receipt.name())
                || !content.mediaType().equals(receipt.mediaType())
                || content.sizeBytes() != receipt.sizeBytes()) {
            throw exception(FILE_STORAGE_RECEIPT_CONFLICT);
        }
    }

    private String effectiveClientDigest(String initializedDigest, String completingDigest) {
        String initialized = normalizeDigest(initializedDigest);
        String completing = normalizeDigest(completingDigest);
        if (initialized != null && completing != null && !initialized.equals(completing)) {
            throw exception(FILE_DIGEST_MISMATCH);
        }
        return completing == null ? initialized : completing;
    }

    private String completionRequestDigest(FileUploadCompleteCommand command, String actualSha256) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", command.tenantId());
        request.put("actorUserId", command.actorUserId());
        request.put("artifactId", command.artifactId());
        request.put("sessionId", command.sessionId());
        request.put("actualSha256", actualSha256);
        return sha256(JsonUtils.toJsonString(request));
    }

    private static String contentSha256(byte[] content) {
        return sha256Bytes(content);
    }

    private static CompletionFacts requireCompletion(AtomicReference<CompletionFacts> completionRef) {
        CompletionFacts facts = completionRef.get();
        if (facts == null) {
            throw new IllegalStateException("FILE_UPLOAD_COMPLETION_FACT_MISSING");
        }
        return facts;
    }

    private static Object auditValue(Object value) {
        return value == null ? "NONE" : value;
    }

    private static String auditText(String value) {
        return value == null ? null : value.trim();
    }

    private static void putIfNotNull(Map<String, Object> detail, String key, Object value) {
        if (value != null) {
            detail.put(key, value);
        }
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof ServiceException serviceException) {
            return String.valueOf(serviceException.getCode());
        }
        if (failure.getMessage() != null && failure.getMessage().startsWith("FILE_")) {
            return failure.getMessage();
        }
        return "FILE_UPLOAD_INITIALIZE_FAILED";
    }

    private static FileBusinessObjectPolicyFact requirePolicy(
            AtomicReference<FileBusinessObjectPolicyFact> policyRef) {
        FileBusinessObjectPolicyFact policy = policyRef.get();
        if (policy == null) {
            throw new IllegalStateException("FILE_UPLOAD_POLICY_FACT_MISSING");
        }
        return policy;
    }

    private ValidatedInitialization validate(FileUploadInitializeCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.declaredSizeBytes() == null || command.declaredSizeBytes() <= 0) {
            throw exception(FILE_COMMAND_INVALID);
        }
        String mode = text(command.modeCode());
        if (!Set.of(MODE_CREATE_ARTIFACT, MODE_ADD_VERSION).contains(mode)) {
            throw exception(FILE_COMMAND_INVALID);
        }
        if ((MODE_CREATE_ARTIFACT.equals(mode)
                && (command.artifactId() != null || command.expectedReferenceVersion() != null))
                || (MODE_ADD_VERSION.equals(mode)
                && (command.artifactId() == null || command.artifactId() <= 0
                || command.expectedReferenceVersion() == null || command.expectedReferenceVersion() < 0))) {
            throw exception(FILE_COMMAND_INVALID);
        }
        String idempotencyKey = limitedText(command.idempotencyKey(), 128);
        String ownerContext = limitedText(command.ownerContext(), 32);
        String objectType = limitedText(command.objectType(), 64);
        String objectId = limitedText(command.objectId(), 128);
        String purposeCode = limitedText(command.purposeCode(), 64);
        String referenceKey = limitedText(command.referenceKey(), 128);
        String fileName = limitedText(command.fileName(), 256);
        String categoryCode = limitedText(command.categoryCode(), 64);
        String mediaType = normalizeMediaType(command.declaredMediaType());
        normalizeDigest(command.clientSha256());
        return new ValidatedInitialization(mode, idempotencyKey, ownerContext, objectType, objectId,
                purposeCode, referenceKey, fileName, categoryCode, mediaType,
                MODE_CREATE_ARTIFACT.equals(mode) ? FileActionCodes.UPLOAD : FileActionCodes.REPLACE);
    }

    private void validatePolicy(ValidatedInitialization command, long declaredSizeBytes,
                                FileBusinessObjectPolicyFact policy) {
        if (!policy.allowedCategoryCodes().contains(command.categoryCode())
                || !normalizedMediaTypes(policy.allowedMediaTypes()).contains(command.declaredMediaType())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        long maxBytes = Math.min(PLATFORM_MAX_BYTES, policy.maxSizeBytes());
        if (maxBytes <= 0) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        if (declaredSizeBytes > maxBytes) {
            throw exception(FILE_SIZE_EXCEEDED);
        }
        if (MODE_ADD_VERSION.equals(command.modeCode())
                && !"MUTABLE".equals(policy.referenceMutability())) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
    }

    private String requestDigest(FileUploadInitializeCommand command, ValidatedInitialization validated) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", command.tenantId());
        request.put("actorUserId", command.actorUserId());
        request.put("modeCode", validated.modeCode());
        request.put("artifactId", command.artifactId());
        request.put("expectedReferenceVersion", command.expectedReferenceVersion());
        request.put("ownerContext", validated.ownerContext());
        request.put("objectType", validated.objectType());
        request.put("objectId", validated.objectId());
        request.put("purposeCode", validated.purposeCode());
        request.put("referenceKey", validated.referenceKey());
        request.put("fileName", validated.fileName());
        request.put("categoryCode", validated.categoryCode());
        request.put("declaredSizeBytes", command.declaredSizeBytes());
        request.put("declaredMediaType", validated.declaredMediaType());
        request.put("clientSha256", normalizeDigest(command.clientSha256()));
        return sha256(JsonUtils.toJsonString(request));
    }

    private static String sha256(String value) {
        return sha256Bytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Bytes(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Set<String> normalizedMediaTypes(Set<String> mediaTypes) {
        return mediaTypes.stream()
                .map(FileUploadApplicationService::normalizeMediaType)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizeMediaType(String value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);
        int parameter = normalized.indexOf(';');
        return parameter < 0 ? normalized : normalized.substring(0, parameter).trim();
    }

    private static String normalizeDigest(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return normalized;
    }

    private static String limitedText(String value, int maxLength) {
        String normalized = text(value);
        if (normalized.length() > maxLength) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return normalized;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            throw exception(FILE_COMMAND_INVALID);
        }
        return value.trim();
    }

    private record ValidatedInitialization(
            String modeCode, String idempotencyKey, String ownerContext, String objectType,
            String objectId, String purposeCode, String referenceKey, String fileName,
            String categoryCode, String declaredMediaType, String action) {
    }

    private record CompletionFacts(
            Long artifactId,
            Integer versionNo,
            Long referenceId,
            FileUploadSessionDO session,
            FileBusinessObjectPolicyFact policy,
            ValidatedFileContent content,
            FileStorageReceipt receipt,
            LocalDateTime occurredAt) {
    }
}
