package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageReceipt;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileArtifactDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileReferenceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileArtifactMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileReferenceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.*;
import cn.iocoder.yudao.module.pms.platform.service.file.command.BoundedFileContentValidationCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.GeneratedBusinessFilePersistence;
import cn.iocoder.yudao.module.pms.platform.service.file.command.GeneratedBusinessFileReservation;
import cn.iocoder.yudao.module.pms.platform.service.file.command.ValidatedFileContent;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.*;

@Service
public class GeneratedBusinessFileService {

    private static final String OWNER = "ACC";
    private static final String TYPE = "SATISFACTION_RESULT";
    private static final String PURPOSE = "SATISFACTION_RESULT_DOCUMENT";

    private final GeneratedBusinessFileTransactionService transactions;
    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final FileContentPolicyService contentPolicyService;
    private final FileUploadSessionMapper sessionMapper;
    private final FileArtifactMapper artifactMapper;
    private final FileVersionMapper versionMapper;
    private final FileReferenceMapper referenceMapper;
    private final PermissionApi permissionApi;

    public GeneratedBusinessFileService(
            GeneratedBusinessFileTransactionService transactions,
            FileBusinessObjectPolicyRegistry policyRegistry,
            FileContentPolicyService contentPolicyService,
            FileUploadSessionMapper sessionMapper,
            FileArtifactMapper artifactMapper,
            FileVersionMapper versionMapper,
            FileReferenceMapper referenceMapper,
            PermissionApi permissionApi) {
        this.transactions = transactions;
        this.policyRegistry = policyRegistry;
        this.contentPolicyService = contentPolicyService;
        this.sessionMapper = sessionMapper;
        this.artifactMapper = artifactMapper;
        this.versionMapper = versionMapper;
        this.referenceMapper = referenceMapper;
        this.permissionApi = permissionApi;
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public FileArtifactVersionFact create(GeneratedBusinessFileCommand command) {
        requireCommand(command);
        if (!command.tenantId().equals(TenantContextHolder.getRequiredTenantId())
                || !permissionApi.hasAnyPermissions(command.actorUserId(), "pms:file:upload")) {
            throw exception(FILE_SCOPE_FORBIDDEN);
        }
        String referenceKey = referenceKey(command.resultId());
        FileBusinessObjectPolicyFact policy = policyRegistry.lockAndRevalidateGeneratedBusinessFile(
                new GeneratedBusinessFilePolicyRevalidationQuery(
                        command.tenantId(), command.actorUserId(), command.resultId(),
                        command.collectionTaskId(), command.questionnaireId(), command.responseId(),
                        command.expectedTaskVersion(), OWNER, TYPE, PURPOSE, referenceKey,
                        FileActionCodes.UPLOAD, command.scopeVersion()));
        ValidatedFileContent content = contentPolicyService.validateBounded(
                new BoundedFileContentValidationCommand(command.content(), command.fileName(),
                        command.content().length, command.contentType(), null, policy));
        GeneratedBusinessFileReservation reservation = transactions.reserve(
                command, content, requestDigest(command, content.sha256()));
        requireTargetAvailable(command, reservation, referenceKey);
        FileStorageReceipt receipt = transactions.store(command, reservation, content);
        GeneratedBusinessFilePersistence persistence = persist(
                command, reservation, content, receipt, policy, referenceKey);
        registerCompletion(command.tenantId(), reservation.sessionId(), persistence, receipt);
        return persistence.fact();
    }

    private GeneratedBusinessFilePersistence persist(
            GeneratedBusinessFileCommand command,
            GeneratedBusinessFileReservation reservation,
            ValidatedFileContent content,
            FileStorageReceipt receipt,
            FileBusinessObjectPolicyFact policy,
            String referenceKey) {
        FileUploadSessionDO session = sessionMapper.selectForUpdate(
                new FileUploadSessionLockQuery(command.tenantId(), reservation.sessionId()));
        if (session == null || !"INITIALIZED".equals(session.getStatusCode())
                || !reservation.artifactId().equals(session.getArtifactId())
                || !receipt.infraFileId().equals(session.getRegisteredInfraFileId())
                || !content.sha256().equals(session.getActualSha256())) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        FileReferenceLockQuery referenceQuery = new FileReferenceLockQuery(command.tenantId(),
                OWNER, TYPE, String.valueOf(command.resultId()), PURPOSE, referenceKey);
        FileReferenceDO existingReference = referenceMapper.selectForUpdate(referenceQuery);
        if (existingReference != null) {
            return requireExisting(command, reservation, content, existingReference);
        }
        if (artifactMapper.selectForUpdate(new FileArtifactLockQuery(
                command.tenantId(), reservation.artifactId())) != null) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        FileArtifactDO artifact = new FileArtifactDO();
        artifact.setId(reservation.artifactId());
        artifact.setName(command.fileName());
        artifact.setCategoryCode(PURPOSE);
        artifact.setOwnerContext(OWNER);
        artifact.setLifecycleStatusCode("DRAFT");
        artifact.setVersion(0);
        artifact.setCreator(String.valueOf(command.actorUserId()));
        artifact.setUpdater(String.valueOf(command.actorUserId()));
        artifact.setTenantId(command.tenantId());
        if (artifactMapper.insert(artifact) != 1) {
            throw new IllegalStateException("GENERATED_FILE_ARTIFACT_CREATE_FAILED");
        }

        FileVersionDO version = new FileVersionDO();
        version.setArtifactId(artifact.getId());
        version.setVersionNo(1);
        version.setInfraFileId(receipt.infraFileId());
        version.setAvailabilityVersion(0);
        version.setSha256(content.sha256());
        version.setSizeBytes(content.sizeBytes());
        version.setDeclaredMediaType(command.contentType());
        version.setDetectedMediaType(content.mediaType());
        version.setScanStatusCode(content.scanStatusCode());
        version.setScanProviderCode(content.scanProviderCode());
        version.setScanProviderVersion(content.scanProviderVersion());
        version.setAvailabilityStatusCode("AVAILABLE");
        version.setCreatedBy(command.actorUserId());
        version.setCreatedAt(now);
        version.setTenantId(command.tenantId());
        if (versionMapper.insert(version) != 1) {
            throw new IllegalStateException("GENERATED_FILE_VERSION_CREATE_FAILED");
        }

        FileReferenceDO reference = new FileReferenceDO();
        reference.setOwnerContext(OWNER);
        reference.setObjectType(TYPE);
        reference.setObjectId(String.valueOf(command.resultId()));
        reference.setPurposeCode(PURPOSE);
        reference.setReferenceKey(referenceKey);
        reference.setArtifactId(artifact.getId());
        reference.setFileVersionNo(1);
        reference.setSensitivityCode(policy.sensitivityCode());
        reference.setStatusCode("ACTIVE");
        reference.setScopeVersion(policy.scopeVersion());
        reference.setVersion(0);
        reference.setCreator(String.valueOf(command.actorUserId()));
        reference.setUpdater(String.valueOf(command.actorUserId()));
        reference.setTenantId(command.tenantId());
        if (referenceMapper.insert(reference) != 1
                || artifactMapper.activateDraftIfMatch(new FileArtifactActivationUpdate(
                command.tenantId(), artifact.getId(), 0)) != 1) {
            throw new IllegalStateException("GENERATED_FILE_REFERENCE_CREATE_FAILED");
        }
        return new GeneratedBusinessFilePersistence(new FileArtifactVersionFact(
                artifact.getId(), 1, referenceKey, PURPOSE, command.fileName(), content.sizeBytes(),
                content.mediaType(), content.sha256(), "AVAILABLE", "ACTIVE",
                new FileFactVersion(1, 0, 0), policy.scopeVersion()), reference.getId());
    }

    private GeneratedBusinessFilePersistence requireExisting(
            GeneratedBusinessFileCommand command,
            GeneratedBusinessFileReservation reservation,
            ValidatedFileContent content,
            FileReferenceDO reference) {
        if (!reservation.artifactId().equals(reference.getArtifactId())
                || reference.getFileVersionNo() == null || !"ACTIVE".equals(reference.getStatusCode())
                || !command.scopeVersion().equals(reference.getScopeVersion())) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
        FileArtifactDO artifact = artifactMapper.selectForUpdate(
                new FileArtifactLockQuery(command.tenantId(), reference.getArtifactId()));
        FileVersionDO version = versionMapper.selectForUpdate(new FileVersionLockQuery(
                command.tenantId(), reference.getArtifactId(), reference.getFileVersionNo()));
        if (artifact == null || version == null || !"ACTIVE".equals(artifact.getLifecycleStatusCode())
                || !"AVAILABLE".equals(version.getAvailabilityStatusCode())
                || !content.sha256().equals(version.getSha256())) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
        return new GeneratedBusinessFilePersistence(new FileArtifactVersionFact(
                artifact.getId(), version.getVersionNo(), reference.getReferenceKey(),
                artifact.getCategoryCode(), artifact.getName(), version.getSizeBytes(),
                version.getDetectedMediaType(), version.getSha256(), version.getAvailabilityStatusCode(),
                reference.getStatusCode(), new FileFactVersion(artifact.getVersion(), reference.getVersion(),
                version.getAvailabilityVersion()), reference.getScopeVersion()), reference.getId());
    }

    private void requireTargetAvailable(GeneratedBusinessFileCommand command,
                                        GeneratedBusinessFileReservation reservation,
                                        String referenceKey) {
        FileReferenceDO existing = referenceMapper.selectExact(new ExactFileReferenceQuery(
                command.tenantId(), OWNER, TYPE, String.valueOf(command.resultId()), PURPOSE, referenceKey));
        if (existing != null && !reservation.artifactId().equals(existing.getArtifactId())) {
            throw exception(FILE_REFERENCE_VERSION_CONFLICT);
        }
    }

    private void registerCompletion(Long tenantId, Long sessionId,
                                    GeneratedBusinessFilePersistence persistence,
                                    FileStorageReceipt receipt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw exception(FILE_UPLOAD_SESSION_STATE_INVALID);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    transactions.completeSession(tenantId, sessionId, persistence, receipt);
                }
            }
        });
    }

    private void requireCommand(GeneratedBusinessFileCommand command) {
        if (command == null || !OWNER.equals(command.ownerContext()) || !TYPE.equals(command.objectType())
                || !PURPOSE.equals(command.purposeCode())) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }

    private String requestDigest(GeneratedBusinessFileCommand command, String contentSha256) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tenantId", command.tenantId());
        request.put("actorUserId", command.actorUserId());
        request.put("operationId", command.operationId());
        request.put("resultId", command.resultId());
        request.put("collectionTaskId", command.collectionTaskId());
        request.put("questionnaireId", command.questionnaireId());
        request.put("responseId", command.responseId());
        request.put("expectedTaskVersion", command.expectedTaskVersion());
        request.put("ownerContext", command.ownerContext());
        request.put("objectType", command.objectType());
        request.put("purposeCode", command.purposeCode());
        request.put("scopeVersion", command.scopeVersion());
        request.put("fileName", command.fileName());
        request.put("contentType", command.contentType());
        request.put("contentSha256", contentSha256);
        return sha256(JsonUtils.toJsonString(request));
    }

    private static String referenceKey(Long resultId) {
        return "satisfaction-result-" + resultId;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
