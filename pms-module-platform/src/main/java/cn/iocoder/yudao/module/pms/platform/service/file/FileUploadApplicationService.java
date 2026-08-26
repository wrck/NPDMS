package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileBusinessObjectPolicyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileUploadSessionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileUploadSessionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitializeCommand;
import cn.iocoder.yudao.module.pms.platform.service.file.command.FileUploadInitialized;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
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

@Service
public class FileUploadApplicationService {

    static final String MODE_CREATE_ARTIFACT = "CREATE_ARTIFACT";
    static final String MODE_ADD_VERSION = "ADD_VERSION";
    static final long PLATFORM_MAX_BYTES = 52_428_800L;

    private final FileUploadSessionMapper sessionMapper;
    private final FileBusinessObjectPolicyRegistry policyRegistry;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final Duration sessionTtl;

    public FileUploadApplicationService(
            FileUploadSessionMapper sessionMapper,
            FileBusinessObjectPolicyRegistry policyRegistry,
            PlatformCommandExecutionApi commandExecutionApi,
            @Value("${pms.file.upload.session-ttl:PT15M}") Duration sessionTtl) {
        this.sessionMapper = sessionMapper;
        this.policyRegistry = policyRegistry;
        this.commandExecutionApi = commandExecutionApi;
        this.sessionTtl = sessionTtl;
    }

    public FileUploadInitialized initialize(FileUploadInitializeCommand command) {
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
                initialized -> successFacts(initialized, validated, requirePolicy(policyRef)));
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
            FileUploadInitialized initialized, ValidatedInitialization validated,
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
        detail.put("categoryCode", validated.categoryCode());
        detail.put("scopeVersion", policy.scopeVersion());
        detail.put("expiresAt", initialized.expiresAt());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "FILE_UPLOAD_INITIALIZE", "FileUploadSession", String.valueOf(initialized.sessionId()),
                validated.idempotencyKey(), JsonUtils.toJsonString(detail), null, null);
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
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8)));
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
}
