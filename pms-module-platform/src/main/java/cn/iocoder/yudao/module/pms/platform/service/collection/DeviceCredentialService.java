package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.DeviceCredentialApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialDTO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CredentialGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.DeviceCredentialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CredentialGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.DeviceCredentialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.EffectiveCredentialGrantQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceCredentialService implements DeviceCredentialApi {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final DeviceCredentialMapper credentialMapper;
    private final CredentialGrantMapper grantMapper;
    private final CredentialSecretProtector secretProtector;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceCredentialDTO create(DeviceCredentialCreateCommand command) {
        validateCreate(command);
        if (!blank(command.kmsReference())) {
            throw new IllegalStateException("KMS_CREDENTIAL_RESOLVER_NOT_CONFIGURED");
        }
        char[] secret = command.secret();
        String protectedSecret = null;
        try {
            if (command.kmsReference() == null) {
                protectedSecret = secretProtector.protect(secret);
            }
            DeviceCredentialDO credential = new DeviceCredentialDO();
            credential.setTenantId(command.tenantId());
            credential.setCredentialCode(command.credentialCode().trim());
            credential.setCredentialType(command.credentialType());
            credential.setUsername(command.username().trim());
            credential.setEncryptedSecret(protectedSecret);
            credential.setKmsReference(blankToNull(command.kmsReference()));
            credential.setCredentialVersion(1L);
            credential.setStatus(STATUS_ACTIVE);
            if (credentialMapper.insert(credential) != 1) {
                throw new IllegalStateException("DEVICE_CREDENTIAL_CREATE_FAILED");
            }

            CredentialGrantDO grant = new CredentialGrantDO();
            grant.setTenantId(command.tenantId());
            grant.setCredentialId(credential.getId());
            grant.setGranteeType("USER");
            grant.setGranteeId(String.valueOf(command.actorId()));
            grant.setDeviceId(command.deviceId());
            grant.setProtocol(command.credentialType());
            grant.setCommandTemplateId(command.commandTemplateId());
            grant.setExpiresAt(command.expiresAt());
            grant.setStatus(STATUS_ACTIVE);
            if (grantMapper.insert(grant) != 1) {
                throw new IllegalStateException("CREDENTIAL_GRANT_CREATE_FAILED");
            }
            return toDTO(credential, grant.getId());
        } finally {
            if (secret != null) {
                Arrays.fill(secret, '\0');
            }
        }
    }

    @Override
    public DeviceCredentialDTO get(Long tenantId, Long credentialId) {
        validateIdentity(tenantId, credentialId);
        DeviceCredentialDO credential = credentialMapper.selectByTenantAndId(tenantId, credentialId);
        return credential == null ? null : toDTO(credential, null);
    }

    public ResolvedCredential resolve(CredentialAccessRequest request) {
        validateAccess(request);
        DeviceCredentialDO credential = credentialMapper.selectByTenantAndId(request.tenantId(), request.credentialId());
        if (credential == null || !STATUS_ACTIVE.equals(credential.getStatus())) {
            throw new IllegalStateException("DEVICE_CREDENTIAL_NOT_ACTIVE");
        }
        List<CredentialGrantDO> grants = grantMapper.selectEffective(new EffectiveCredentialGrantQuery(
                request.tenantId(), request.credentialId(), "USER", String.valueOf(request.actorId()),
                request.deviceId(), request.protocol(), request.commandTemplateId(),
                LocalDateTime.now(clock)));
        if (grants.size() != 1) {
            throw new IllegalStateException(grants.isEmpty()
                    ? "CREDENTIAL_GRANT_NOT_FOUND" : "CREDENTIAL_GRANT_CONFLICT");
        }
        if (credential.getEncryptedSecret() == null) {
            throw new IllegalStateException("KMS_CREDENTIAL_RESOLVER_NOT_CONFIGURED");
        }
        char[] secret = secretProtector.reveal(credential.getEncryptedSecret());
        return new ResolvedCredential(credential.getUsername(), secret, credential.getCredentialVersion(), grants.get(0).getId());
    }

    private static DeviceCredentialDTO toDTO(DeviceCredentialDO credential, Long defaultGrantId) {
        return new DeviceCredentialDTO(credential.getId(), credential.getCredentialCode(),
                credential.getCredentialType(), credential.getUsername(), "********",
                credential.getCredentialVersion(), credential.getStatus(), defaultGrantId);
    }

    private static void validateCreate(DeviceCredentialCreateCommand command) {
        if (command == null || command.tenantId() == null || command.actorId() == null || command.actorId() <= 0
                || blank(command.credentialCode()) || blank(command.username())
                || !List.of("SSH", "TELNET").contains(command.credentialType())
                || blank(command.deviceId()) || blank(command.commandTemplateId())) {
            throw new IllegalArgumentException("凭证创建参数不完整");
        }
        boolean hasSecret = command.secret() != null && command.secret().length > 0;
        boolean hasKmsReference = !blank(command.kmsReference());
        if (hasSecret == hasKmsReference) {
            throw new IllegalArgumentException("凭证必须且只能提供秘密或KMS引用");
        }
    }

    private static void validateIdentity(Long tenantId, Long credentialId) {
        if (tenantId == null || credentialId == null || credentialId <= 0) {
            throw new IllegalArgumentException("凭证标识无效");
        }
    }

    private static void validateAccess(CredentialAccessRequest request) {
        if (request == null || request.tenantId() == null || request.actorId() == null || request.actorId() <= 0
                || request.credentialId() == null || request.credentialId() <= 0 || blank(request.deviceId())
                || !List.of("SSH", "TELNET").contains(request.protocol()) || blank(request.commandTemplateId())) {
            throw new IllegalArgumentException("凭证访问参数不完整");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    public record CredentialAccessRequest(
            Long tenantId,
            Long actorId,
            Long credentialId,
            String deviceId,
            String protocol,
            String commandTemplateId) {
    }

    public static final class ResolvedCredential implements AutoCloseable {

        private final String username;
        private final char[] secret;
        private final Long credentialVersion;
        private final Long grantSnapshotId;

        private ResolvedCredential(String username, char[] secret, Long credentialVersion, Long grantSnapshotId) {
            this.username = username;
            this.secret = secret;
            this.credentialVersion = credentialVersion;
            this.grantSnapshotId = grantSnapshotId;
        }

        public String username() {
            return username;
        }

        public char[] secret() {
            return secret;
        }

        public Long credentialVersion() {
            return credentialVersion;
        }

        public Long grantSnapshotId() {
            return grantSnapshotId;
        }

        @Override
        public void close() {
            Arrays.fill(secret, '\0');
        }
    }
}
