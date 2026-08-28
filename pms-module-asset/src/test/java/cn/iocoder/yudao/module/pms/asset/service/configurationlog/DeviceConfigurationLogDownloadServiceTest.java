package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceDownloadGrantDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.DeviceDownloadGrantMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.EquipmentConfigLogMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceConfigurationLogDownloadServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T10:00:00Z");

    @Mock private DeviceMapper deviceMapper;
    @Mock private EquipmentConfigLogMapper configurationLogMapper;
    @Mock private DeviceDownloadGrantMapper grantMapper;
    @Mock private PermissionApi permissionApi;
    @Mock private FileApi fileApi;
    @Mock private DeviceConfigurationFileContentClient contentClient;
    @Mock private DeviceAccessScopeService accessScopeService;
    private DeviceConfigurationLogDownloadService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        service = new DeviceConfigurationLogDownloadService(
                deviceMapper, configurationLogMapper, grantMapper, permissionApi, fileApi, contentClient,
                accessScopeService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDeclareProductionConstructorForSpringInjection() throws NoSuchMethodException {
        assertTrue(DeviceConfigurationLogDownloadService.class.getConstructor(
                DeviceMapper.class, EquipmentConfigLogMapper.class, DeviceDownloadGrantMapper.class,
                PermissionApi.class, FileApi.class, DeviceConfigurationFileContentClient.class,
                DeviceAccessScopeService.class)
                .isAnnotationPresent(Autowired.class));
    }

    @Test
    void shouldIssueUserBoundGrantWithDigestOnly() {
        allowDownload();
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(configurationLogMapper.selectById(21L)).thenReturn(configurationLog());

        DeviceConfigurationDownloadGrant result = service.issueGrant(1L, 7L, 8L, 21L);

        assertTrue(result.downloadPath().startsWith("/pms/asset/devices/8/configuration-logs/download?token="));
        assertEquals(LocalDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC), result.expiresAt());
        ArgumentCaptor<DeviceDownloadGrantDO> captor = ArgumentCaptor.forClass(DeviceDownloadGrantDO.class);
        verify(grantMapper).insert(captor.capture());
        DeviceDownloadGrantDO saved = captor.getValue();
        String rawToken = result.downloadPath().substring(result.downloadPath().indexOf("token=") + 6);
        assertNotEquals(rawToken, saved.getTokenDigest());
        assertEquals(64, saved.getTokenDigest().length());
        assertEquals(1L, saved.getTenantId());
        assertEquals(7L, saved.getUserId());
        assertEquals("SN-8", saved.getDeviceSn());
        assertEquals(21L, saved.getConfigurationLogId());
    }

    @Test
    void shouldRejectGrantWhenDeviceIsInvisible() {
        allowDownload();
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(null);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.issueGrant(1L, 7L, 8L, 21L));

        assertEquals(AST_EQUIPMENT_NOT_EXISTS.getCode(), error.getCode());
        verify(configurationLogMapper, never()).selectById(21L);
        verify(grantMapper, never()).insert(any(DeviceDownloadGrantDO.class));
    }

    @Test
    void shouldRejectGrantWithoutDownloadPermission() {
        when(permissionApi.hasAnyPermissions(7L, DeviceConfigurationLogDownloadService.DOWNLOAD_PERMISSION))
                .thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.issueGrant(1L, 7L, 8L, 21L));

        assertEquals(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_FORBIDDEN.getCode(), error.getCode());
        verify(deviceMapper, never()).selectByTenantAndId(1L, 8L);
    }

    @Test
    void shouldRejectLogThatDoesNotBelongToDevice() {
        allowDownload();
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        EquipmentConfigLogDO log = configurationLog();
        log.setEquipmentId(9L);
        when(configurationLogMapper.selectById(21L)).thenReturn(log);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.issueGrant(1L, 7L, 8L, 21L));

        assertEquals(AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS.getCode(), error.getCode());
        verify(grantMapper, never()).insert(any(DeviceDownloadGrantDO.class));
    }

    @Test
    void shouldRejectLogWithoutFile() {
        allowDownload();
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        EquipmentConfigLogDO log = configurationLog();
        log.setFileUrl(" ");
        when(configurationLogMapper.selectById(21L)).thenReturn(log);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.issueGrant(1L, 7L, 8L, 21L));

        assertEquals(AST_EQUIPMENT_CONFIG_LOG_NOT_EXISTS.getCode(), error.getCode());
        verify(grantMapper, never()).insert(any(DeviceDownloadGrantDO.class));
    }

    @Test
    void shouldConsumeGrantOnceAndStreamThroughInternalPresignedUrl() {
        allowDownload();
        DeviceDownloadGrantDO grant = grant(7L, NOW.plusSeconds(60), null);
        when(grantMapper.selectByTokenDigest(service.digest("raw-token"))).thenReturn(grant);
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(configurationLogMapper.selectById(21L)).thenReturn(configurationLog());
        when(grantMapper.consume(1L, grant.getTokenDigest(), 7L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .thenReturn(1);
        when(fileApi.presignGetUrl("https://storage.example/config/21.txt", 60))
                .thenReturn("https://signed.example/config/21.txt?signature=secret");
        InputStream content = new ByteArrayInputStream("config".getBytes());
        when(contentClient.open("https://signed.example/config/21.txt?signature=secret")).thenReturn(content);

        DeviceConfigurationFileContent result = service.download(1L, 7L, 8L, "raw-token");

        assertEquals("configuration-log-21.txt", result.fileName());
        assertEquals(content, result.content());
        verify(fileApi).presignGetUrl("https://storage.example/config/21.txt", 60);
        verify(contentClient).open("https://signed.example/config/21.txt?signature=secret");
    }

    @Test
    void shouldRejectExpiredToken() {
        DeviceDownloadGrantDO grant = grant(7L, NOW.minusSeconds(1), null);
        when(grantMapper.selectByTokenDigest(service.digest("raw-token"))).thenReturn(grant);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.download(1L, 7L, 8L, "raw-token"));

        assertEquals(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID.getCode(), error.getCode());
        verify(deviceMapper, never()).selectByTenantAndId(1L, 8L);
    }

    @Test
    void shouldRejectForwardedTokenFromOtherUser() {
        DeviceDownloadGrantDO grant = grant(9L, NOW.plusSeconds(60), null);
        when(grantMapper.selectByTokenDigest(service.digest("raw-token"))).thenReturn(grant);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.download(1L, 7L, 8L, "raw-token"));

        assertEquals(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID.getCode(), error.getCode());
        verify(deviceMapper, never()).selectByTenantAndId(1L, 8L);
    }

    @Test
    void shouldRejectRepeatedConsumption() {
        DeviceDownloadGrantDO grant = grant(7L, NOW.plusSeconds(60), LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(grantMapper.selectByTokenDigest(service.digest("raw-token"))).thenReturn(grant);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.download(1L, 7L, 8L, "raw-token"));

        assertEquals(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID.getCode(), error.getCode());
        verify(deviceMapper, never()).selectByTenantAndId(1L, 8L);
    }

    @Test
    void shouldRejectPermissionRevocationAtDownloadTime() {
        DeviceDownloadGrantDO grant = grant(7L, NOW.plusSeconds(60), null);
        when(grantMapper.selectByTokenDigest(service.digest("raw-token"))).thenReturn(grant);
        when(permissionApi.hasAnyPermissions(7L, DeviceConfigurationLogDownloadService.DOWNLOAD_PERMISSION))
                .thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.download(1L, 7L, 8L, "raw-token"));

        assertEquals(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_FORBIDDEN.getCode(), error.getCode());
        verify(grantMapper, never()).consume(any(), any(), any(), any());
        verify(fileApi, never()).presignGetUrl(any(), any());
    }

    @Test
    void shouldRejectConcurrentSecondConsumption() {
        allowDownload();
        DeviceDownloadGrantDO grant = grant(7L, NOW.plusSeconds(60), null);
        when(grantMapper.selectByTokenDigest(service.digest("raw-token"))).thenReturn(grant);
        when(deviceMapper.selectByTenantAndId(1L, 8L)).thenReturn(device());
        when(configurationLogMapper.selectById(21L)).thenReturn(configurationLog());
        when(grantMapper.consume(1L, grant.getTokenDigest(), 7L, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.download(1L, 7L, 8L, "raw-token"));

        assertEquals(AST_DEVICE_CONFIGURATION_LOG_DOWNLOAD_INVALID.getCode(), error.getCode());
        verify(fileApi, never()).presignGetUrl(any(), any());
    }

    @Test
    void shouldRejectUnsupportedFileProtocol() {
        DeviceConfigurationFileContentClient client = new DeviceConfigurationFileContentClient(mock(HttpClient.class));

        assertThrows(IllegalArgumentException.class, () -> client.open("file:///tmp/config.txt"));
    }

    @Test
    void shouldRejectOversizedFileBeforeReturningStream() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.headers()).thenReturn(HttpHeaders.of(
                java.util.Map.of("Content-Length", java.util.List.of("20971521")), (name, value) -> true));
        InputStream body = new ByteArrayInputStream(new byte[0]);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        DeviceConfigurationFileContentClient client = new DeviceConfigurationFileContentClient(httpClient);

        assertThrows(IllegalStateException.class, () -> client.open("https://signed.example/config.txt"));
    }

    private void allowDownload() {
        when(permissionApi.hasAnyPermissions(7L, DeviceConfigurationLogDownloadService.DOWNLOAD_PERMISSION))
                .thenReturn(true);
    }

    private DeviceDO device() {
        DeviceDO device = new DeviceDO();
        device.setId(8L);
        device.setSn("SN-8");
        device.setTenantId(1L);
        return device;
    }

    private EquipmentConfigLogDO configurationLog() {
        EquipmentConfigLogDO log = new EquipmentConfigLogDO();
        log.setId(21L);
        log.setEquipmentId(8L);
        log.setFileUrl("https://storage.example/config/21.txt");
        log.setTenantId(1L);
        return log;
    }

    private DeviceDownloadGrantDO grant(Long userId, Instant expiresAt, LocalDateTime consumedAt) {
        DeviceDownloadGrantDO grant = new DeviceDownloadGrantDO();
        grant.setId(31L);
        grant.setTokenDigest(service.digest("raw-token"));
        grant.setUserId(userId);
        grant.setDeviceSn("SN-8");
        grant.setConfigurationLogId(21L);
        grant.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        grant.setConsumedAt(consumedAt);
        grant.setTenantId(1L);
        return grant;
    }
}
