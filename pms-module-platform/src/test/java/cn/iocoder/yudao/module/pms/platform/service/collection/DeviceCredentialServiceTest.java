package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialCreateCommand;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.DeviceCredentialDTO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CredentialGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.DeviceCredentialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CredentialGrantMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.DeviceCredentialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceCredentialServiceTest {

    @Mock DeviceCredentialMapper credentialMapper;
    @Mock CredentialGrantMapper grantMapper;
    @Mock CredentialSecretProtector secretProtector;

    private DeviceCredentialService service;

    @BeforeEach
    void setUp() {
        service = new DeviceCredentialService(credentialMapper, grantMapper, secretProtector,
                Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createPersistsProtectedSecretAndReturnsOnlyMaskedMetadata() {
        char[] secret = "sensitive-value".toCharArray();
        when(secretProtector.protect(any(char[].class))).thenReturn("protected-value");
        when(credentialMapper.insert(any(DeviceCredentialDO.class))).thenAnswer(invocation -> {
            invocation.<DeviceCredentialDO>getArgument(0).setId(101L);
            return 1;
        });
        when(grantMapper.insert(any(CredentialGrantDO.class))).thenAnswer(invocation -> {
            invocation.<CredentialGrantDO>getArgument(0).setId(201L);
            return 1;
        });

        DeviceCredentialDTO created = service.create(command(secret));

        ArgumentCaptor<DeviceCredentialDO> credentialCaptor = ArgumentCaptor.forClass(DeviceCredentialDO.class);
        verify(credentialMapper).insert(credentialCaptor.capture());
        assertEquals("protected-value", credentialCaptor.getValue().getEncryptedSecret());
        assertFalse(credentialCaptor.getValue().getEncryptedSecret().contains("sensitive-value"));
        assertEquals("********", created.secretMask());
        assertArrayEquals(new char[15], secret);

        ArgumentCaptor<CredentialGrantDO> grantCaptor = ArgumentCaptor.forClass(CredentialGrantDO.class);
        verify(grantMapper).insert(grantCaptor.capture());
        assertEquals(101L, grantCaptor.getValue().getCredentialId());
        assertEquals("device-1", grantCaptor.getValue().getDeviceId());
        assertEquals("SSH", grantCaptor.getValue().getProtocol());
        assertEquals("template-1", grantCaptor.getValue().getCommandTemplateId());
    }

    @Test
    void createFailsClosedWhenProtectionFailsAndDoesNotPersist() {
        char[] secret = "sensitive-value".toCharArray();
        when(secretProtector.protect(any(char[].class))).thenThrow(new IllegalStateException("KMS_UNAVAILABLE"));

        assertThrows(IllegalStateException.class, () -> service.create(command(secret)));

        assertArrayEquals(new char[15], secret);
    }

    @Test
    void createRejectsKmsReferenceWhenResolverIsUnavailable() {
        DeviceCredentialCreateCommand command = new DeviceCredentialCreateCommand(
                0L, 7L, "credential-kms", "SSH", "operator", null, "kms://credential/1",
                "device-1", "template-1", null);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.create(command));

        assertEquals("KMS_CREDENTIAL_RESOLVER_NOT_CONFIGURED", failure.getMessage());
    }

    @Test
    void resolveRequiresExactEffectiveGrantAndReturnsClearableSecret() {
        DeviceCredentialDO credential = new DeviceCredentialDO();
        credential.setId(101L);
        credential.setTenantId(0L);
        credential.setUsername("operator");
        credential.setEncryptedSecret("protected-value");
        credential.setStatus("ACTIVE");
        credential.setCredentialVersion(1L);
        when(credentialMapper.selectByTenantAndId(0L, 101L)).thenReturn(credential);
        when(grantMapper.selectEffective(any())).thenReturn(java.util.List.of(new CredentialGrantDO().setId(201L)));
        when(secretProtector.reveal("protected-value")).thenReturn("resolved-secret".toCharArray());

        try (DeviceCredentialService.ResolvedCredential resolved = service.resolve(
                new DeviceCredentialService.CredentialAccessRequest(
                        0L, 7L, 101L, "device-1", "SSH", "template-1"))) {
            assertEquals("operator", resolved.username());
            assertArrayEquals("resolved-secret".toCharArray(), resolved.secret());
        }
    }

    @Test
    void resolveRejectsMissingGrantWithoutDecrypting() {
        DeviceCredentialDO credential = new DeviceCredentialDO();
        credential.setId(101L);
        credential.setTenantId(0L);
        credential.setStatus("ACTIVE");
        when(credentialMapper.selectByTenantAndId(0L, 101L)).thenReturn(credential);
        when(grantMapper.selectEffective(any())).thenReturn(java.util.List.of());

        assertThrows(IllegalStateException.class, () -> service.resolve(
                new DeviceCredentialService.CredentialAccessRequest(
                        0L, 7L, 101L, "device-1", "SSH", "template-1")));
    }

    private DeviceCredentialCreateCommand command(char[] secret) {
        return new DeviceCredentialCreateCommand(
                0L, 7L, "credential-1", "SSH", "operator", secret, null,
                "device-1", "template-1", null);
    }
}
