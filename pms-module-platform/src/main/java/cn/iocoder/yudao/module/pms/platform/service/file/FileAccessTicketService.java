package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.api.file.FileStorageReceiptApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileStorageAccessReceipt;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketRespVO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileAccessGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileAccessGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query.FileVersionLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_ACCESS_GRANT_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_VERSION_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class FileAccessTicketService {

    private static final Set<String> PREVIEW_MEDIA_TYPES = Set.of(
            "application/pdf", "image/gif", "image/jpeg", "image/png", "image/webp", "text/plain");

    private final FileArtifactApi fileArtifactApi;
    private final FileVersionMapper versionMapper;
    private final FileAccessGrantMapper accessGrantMapper;
    private final FileStorageReceiptApi storageReceiptApi;
    private final OperationAuditApi operationAuditApi;
    private final SecurityFrameworkService securityFrameworkService;
    private final PlatformTransactionManager transactionManager;

    @Value("${pms.file.access-ticket-ttl:PT2M}")
    private Duration accessTicketTtl;

    public FileAccessTicketRespVO create(AccessCommand command) {
        String correlationId = UUID.randomUUID().toString();
        AccessCommand validated = command;
        try {
            validated = validate(command);
            String permission = FileActionCodes.DOWNLOAD.equals(validated.operationCode())
                    ? "pms:file:download" : "pms:file:preview";
            if (!securityFrameworkService.hasPermission(permission)) {
                throw exception(FILE_SCOPE_FORBIDDEN);
            }
            AccessCommand effective = validated;
            FileAccessTicketRespVO result = new TransactionTemplate(transactionManager)
                    .execute(status -> createInTransaction(effective, correlationId));
            if (result == null) throw exception(FILE_ACCESS_GRANT_INVALID);
            return result;
        } catch (RuntimeException failure) {
            auditRejected(validated, correlationId, failure);
            throw failure;
        }
    }

    private FileAccessTicketRespVO createInTransaction(AccessCommand command, String correlationId) {
        FileArtifactVersionQuery inspectionQuery = new FileArtifactVersionQuery(command.artifactId(),
                command.versionNo(), command.ownerContext(), command.objectType(), command.objectId(),
                command.purposeCode(), command.referenceKey(), command.operationCode());
        FileArtifactVersionFact inspected = fileArtifactApi.inspect(inspectionQuery);
        FileArtifactVersionFact locked = fileArtifactApi.lockAndRevalidate(
                new FileArtifactVersionRevalidationQuery(command.artifactId(), command.versionNo(),
                        command.ownerContext(), command.objectType(), command.objectId(), command.purposeCode(),
                        command.referenceKey(), command.operationCode(), inspected.fileFactVersion(),
                        inspected.scopeVersion()));
        FileVersionDO version = versionMapper.selectForUpdate(new FileVersionLockQuery(
                command.tenantId(), command.artifactId(), command.versionNo()));
        requireAccessibleVersion(command, version);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(accessTicketTtl);
        String opaqueToken = newToken();
        FileAccessGrantDO grant = new FileAccessGrantDO();
        grant.setTenantId(command.tenantId());
        grant.setArtifactId(command.artifactId());
        grant.setFileVersionNo(command.versionNo());
        grant.setSubjectUserId(command.actorUserId());
        grant.setOperationCode(command.operationCode());
        grant.setBusinessScopeHash(scopeDigest(command, locked.scopeVersion()));
        grant.setTokenDigest(sha256(opaqueToken));
        grant.setStatusCode("ACTIVE");
        grant.setExpiresAt(expiresAt);
        grant.setCreatedAt(now);
        if (accessGrantMapper.insert(grant) != 1 || grant.getId() == null) {
            throw new IllegalStateException("FILE_ACCESS_GRANT_CREATE_FAILED");
        }

        FileStorageAccessReceipt receipt = storageReceiptApi.presignGet(
                version.getInfraFileId(), Math.toIntExact(accessTicketTtl.toSeconds()));
        if (receipt == null || receipt.shortLivedUrl() == null || receipt.shortLivedUrl().isBlank()) {
            throw exception(FILE_ACCESS_GRANT_INVALID);
        }
        auditSuccess(command, correlationId, grant, locked, expiresAt);
        return new FileAccessTicketRespVO(grant.getId(), receipt.shortLivedUrl(), expiresAt);
    }

    private void requireAccessibleVersion(AccessCommand command, FileVersionDO version) {
        if (version == null) throw exception(FILE_VERSION_NOT_FOUND);
        if (!"AVAILABLE".equals(version.getAvailabilityStatusCode())) {
            throw exception(FILE_VERSION_UNAVAILABLE);
        }
        if (FileActionCodes.PREVIEW.equals(command.operationCode())
                && !isPreviewable(version.getDetectedMediaType())) {
            throw exception(FILE_ACCESS_GRANT_INVALID);
        }
    }

    private AccessCommand validate(AccessCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.artifactId() == null || command.artifactId() <= 0
                || command.versionNo() == null || command.versionNo() <= 0
                || accessTicketTtl == null || accessTicketTtl.isZero() || accessTicketTtl.isNegative()
                || accessTicketTtl.toSeconds() < 1 || accessTicketTtl.toSeconds() > Integer.MAX_VALUE) {
            throw exception(FILE_COMMAND_INVALID);
        }
        try {
            String action = FileActionCodes.requireSupported(command.operationCode());
            if (!Set.of(FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW).contains(action)) {
                throw new IllegalArgumentException("unsupported access action");
            }
            return new AccessCommand(command.tenantId(), command.actorUserId(), command.artifactId(),
                    command.versionNo(), action,
                    FileActionCodes.requireText(command.ownerContext(), "ownerContext"),
                    FileActionCodes.requireText(command.objectType(), "objectType"),
                    FileActionCodes.requireText(command.objectId(), "objectId"),
                    FileActionCodes.requireText(command.purposeCode(), "purposeCode"),
                    FileActionCodes.requireText(command.referenceKey(), "referenceKey"));
        } catch (IllegalArgumentException ex) {
            throw exception(FILE_COMMAND_INVALID);
        }
    }

    private void auditSuccess(AccessCommand command, String correlationId, FileAccessGrantDO grant,
                              FileArtifactVersionFact fact, LocalDateTime expiresAt) {
        Map<String, Object> detail = auditDetail(command, correlationId);
        detail.put("grantId", grant.getId());
        detail.put("fileFactVersion", fact.fileFactVersion());
        detail.put("scopeVersion", fact.scopeVersion());
        detail.put("grantStatusBefore", "NONE");
        detail.put("grantStatusAfter", "ACTIVE");
        detail.put("expiresAt", expiresAt);
        operationAuditApi.record(command.tenantId(), command.actorUserId(), correlationId,
                "FILE_ACCESS_TICKET_CREATE", "FileArtifact", String.valueOf(command.artifactId()),
                "SUCCESS", Map.copyOf(detail));
    }

    private void auditRejected(AccessCommand command, String correlationId, RuntimeException failure) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.actorUserId() == null || command.actorUserId() <= 0) return;
        Map<String, Object> detail = auditDetail(command, correlationId);
        detail.put("grantStatusAfter", "REJECTED");
        detail.put("failureCode", failureCode(failure));
        operationAuditApi.record(command.tenantId(), command.actorUserId(), correlationId,
                "FILE_ACCESS_TICKET_CREATE", "FileArtifact",
                command.artifactId() == null ? "UNKNOWN" : String.valueOf(command.artifactId()),
                "REJECTED", Map.copyOf(detail));
    }

    private Map<String, Object> auditDetail(AccessCommand command, String correlationId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operationId", correlationId);
        detail.put("action", safe(command.operationCode()));
        detail.put("artifactId", command.artifactId() == null ? "NONE" : command.artifactId());
        detail.put("versionNo", command.versionNo() == null ? "NONE" : command.versionNo());
        detail.put("ownerContext", safe(command.ownerContext()));
        detail.put("objectType", safe(command.objectType()));
        detail.put("objectId", safe(command.objectId()));
        detail.put("purposeCode", safe(command.purposeCode()));
        detail.put("referenceKey", safe(command.referenceKey()));
        return detail;
    }

    private String failureCode(RuntimeException failure) {
        return failure instanceof ServiceException serviceException
                ? String.valueOf(serviceException.getCode()) : failure.getClass().getSimpleName();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) return "NONE";
        String normalized = value.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private String scopeDigest(AccessCommand command, Long scopeVersion) {
        return sha256(String.join("\u001f", String.valueOf(command.tenantId()), command.ownerContext(),
                command.objectType(), command.objectId(), command.purposeCode(), command.referenceKey(),
                String.valueOf(scopeVersion)));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256摘要算法不可用", ex);
        }
    }

    static boolean isPreviewable(String mediaType) {
        if (mediaType == null) return false;
        String normalized = mediaType.toLowerCase().split(";", 2)[0].trim();
        return PREVIEW_MEDIA_TYPES.contains(normalized);
    }

    public record AccessCommand(Long tenantId, Long actorUserId, Long artifactId, Integer versionNo,
                                String operationCode, String ownerContext, String objectType,
                                String objectId, String purposeCode, String referenceKey) {
    }
}
