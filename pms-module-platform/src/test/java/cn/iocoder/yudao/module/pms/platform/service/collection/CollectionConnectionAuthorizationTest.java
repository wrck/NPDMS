package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CollectionConnectionAuthorizationTest {
    DeviceCredentialMapper credentials=mock(DeviceCredentialMapper.class);
    CredentialGrantMapper grants=mock(CredentialGrantMapper.class);
    CollectionAuthorization auth=mock(CollectionAuthorization.class);
    CollectionConnectionService service=new CollectionConnectionService(credentials,grants,null,null,auth,null,null,null,null);
    CollectionTaskDO task;DeviceCredentialDO connection;CredentialGrantDO grant;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        connection=new DeviceCredentialDO();connection.setId(6L);connection.setStatus("ACTIVE");connection.setProjectId(21L);connection.setDeviceId(31L);connection.setCredentialType("SSH");connection.setHost("device.example");connection.setPort(22);
        grant=new CredentialGrantDO();grant.setId(7L);grant.setTenantId(1L);grant.setCredentialId(6L);grant.setStatus("ACTIVE");grant.setGranteeType("USER");grant.setGranteeId("8");grant.setProjectId("21");grant.setDeviceId("31");grant.setProtocol("SSH");grant.setCommandTemplateId("50");grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        task=new CollectionTaskDO();task.setTenantId(1L);task.setCredentialMode("SAVED_CREDENTIAL");task.setCredentialId(6L);task.setGrantSnapshotId(7L);task.setCreator("8");task.setProjectId("21");task.setDeviceId("31");task.setProtocol("SSH");task.setTemplateId("50");task.setHost("device.example");task.setPort(22);
        when(credentials.selectByTenantAndId(1L,6L)).thenReturn(connection);when(grants.selectById(7L)).thenReturn(grant);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void revokedExpiredDisabledOrMismatchedGrantCannotContinue(){
        assertTrue(service.remainsAuthorized(task));
        grant.setStatus("REVOKED");assertFalse(service.remainsAuthorized(task));grant.setStatus("ACTIVE");
        grant.setExpiresAt(LocalDateTime.now().minusSeconds(1));assertFalse(service.remainsAuthorized(task));grant.setExpiresAt(LocalDateTime.now().plusHours(1));
        grant.setGranteeId("9");assertFalse(service.remainsAuthorized(task));grant.setGranteeId("8");
        grant.setCommandTemplateId("51");assertFalse(service.remainsAuthorized(task));grant.setCommandTemplateId("50");
        grant.setTenantId(2L);assertFalse(service.remainsAuthorized(task));grant.setTenantId(1L);
        connection.setStatus("DISABLED");assertFalse(service.remainsAuthorized(task));
    }
    @Test void explicitUnrestrictedGrantSupportsManualCommandsButOldScopeDoesNot() {
        task.setTemplateId("manual-11");assertFalse(service.remainsAuthorized(task));
        grant.setCommandTemplateId(null);assertFalse(service.remainsAuthorized(task));
        grant.setCommandTemplateId("*");assertTrue(service.remainsAuthorized(task));
        task.setTemplateId("another-published-template");assertTrue(service.remainsAuthorized(task));
        grant.setGranteeId("9");assertFalse(service.remainsAuthorized(task));grant.setGranteeId("8");
        grant.setDeviceId("32");assertFalse(service.remainsAuthorized(task));grant.setDeviceId("31");
        grant.setStatus("REVOKED");assertFalse(service.remainsAuthorized(task));
    }
    @Test void deniedSaveAlwaysClearsSecret(){
        doThrow(new org.springframework.security.access.AccessDeniedException("denied")).when(auth).permission(8L,"pms:device-credential:create");
        char[] secret="test-password".toCharArray();var r=new CollectionConnectionService.Save("request-key-123456","test",21L,31L,"device.example",22,"SSH","user",secret,50L,LocalDateTime.now().plusHours(1));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.save(8L,r));assertArrayEquals(new char[secret.length],secret);verifyNoInteractions(credentials,grants);
    }
}
