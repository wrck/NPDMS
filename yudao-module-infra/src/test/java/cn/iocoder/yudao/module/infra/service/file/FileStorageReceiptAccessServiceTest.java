package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileConfigDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileStorageReceiptAccessServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final FileMapper files = mock(FileMapper.class);
    private final FileConfigService configs = mock(FileConfigService.class);
    private final FileClient frozenClient = mock(FileClient.class);
    private final Map<String, String> cache = new HashMap<>();
    private final FileDO file = file();
    private FileStorageReceiptAccessService service;

    @BeforeEach
    void setUp() {
        var web = new WebProperties();
        web.setAdminApi(new WebProperties.Api("/admin-api", "**.controller.admin.**"));
        service = new FileStorageReceiptAccessService(redis, files, configs, web);
        var db = new DBFileClientConfig();
        db.setDomain("https://files.example/base");
        var config = new FileConfigDO();
        config.setId(51L); config.setConfig(db);
        when(configs.getFileConfig(51L)).thenReturn(config);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenAnswer(invocation -> cache.get(invocation.getArgument(0)));
    }

    @Test
    void rejectsUnmanagedFilesBeforeIssuingACapability() {
        file.setPath("public/document.pdf");
        assertThrows(IllegalArgumentException.class, () -> service.issue(file, 60));
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void issuesAnAbsoluteOpaqueUrlWithoutTheFrozenStorageLocation() {
        String url = service.issue(file, 120);
        String token = url.substring(url.indexOf("ticket=") + "ticket=".length());
        var key = ArgumentCaptor.forClass(String.class);
        var value = ArgumentCaptor.forClass(String.class);
        verify(values).set(key.capture(), value.capture(), eq(Duration.ofSeconds(120)));

        URI uri = URI.create(url);
        assertEquals("https://files.example/base/admin-api/infra/file-storage-receipts/content", uri.toString().substring(0, uri.toString().indexOf('?')));
        assertEquals("ticket=" + token, uri.getRawQuery());
        assertNotEquals(token, key.getValue());
        assertFalse(value.getValue().contains(token));
        verify(configs).getFileConfig(51L);
        verify(configs, never()).getMasterFileClient();
    }

    @Test
    void readsOnlyTheFrozenConfigAndMatchingMetadata() throws Exception {
        String token = token(service.issue(file, 120));
        when(files.selectById(501L)).thenReturn(file());
        when(configs.getFileClient(51L)).thenReturn(frozenClient);
        when(frozenClient.getContent("pms-storage-receipts/op-501")).thenReturn(new byte[] {1, 2, 3});

        var content = service.read(token);

        assertNotNull(content);
        assertEquals("receipt.pdf", content.name());
        verify(configs).getFileClient(51L);
        verify(configs, never()).getMasterFileClient();
    }

    @Test
    void rejectsUnknownTokensAndChangedMetadataWithoutReadingStorage() throws Exception {
        assertNull(service.read("A".repeat(43)));
        verifyNoInteractions(files);

        String token = token(service.issue(file, 120));
        var changed = file(); changed.setPath("pms-storage-receipts/other");
        when(files.selectById(501L)).thenReturn(changed);

        assertNull(service.read(token));
        verify(configs, never()).getFileClient(anyLong());
    }

    @Test
    void rejectsExpiredTicketsAndChangedFrozenConfigWithoutReadingStorage() throws Exception {
        String token = token(service.issue(file, 120));
        cache.replaceAll((key, value) -> JsonUtils.toJsonString(new FileStorageReceiptAccessService.ReceiptTicket(
                501L, 51L, "pms-storage-receipts/op-501", "receipt.pdf", "application/pdf", 3L,
                LocalDateTime.now().minusSeconds(1))));

        assertNull(service.read(token));
        verifyNoInteractions(files);

        token = token(service.issue(file, 120));
        var changed = file(); changed.setConfigId(52L);
        when(files.selectById(501L)).thenReturn(changed);

        assertNull(service.read(token));
        verify(configs, never()).getFileClient(anyLong());
    }

    @Test
    void rejectsMissingContent() throws Exception {
        String token = token(service.issue(file, 120));
        when(files.selectById(501L)).thenReturn(file());
        when(configs.getFileClient(51L)).thenReturn(frozenClient);
        when(frozenClient.getContent(anyString())).thenReturn(null);

        assertNull(service.read(token));
    }

    @Test
    void rejectsLengthChangedContent() throws Exception {
        String token = token(service.issue(file, 120));
        when(files.selectById(501L)).thenReturn(file());
        when(configs.getFileClient(51L)).thenReturn(frozenClient);
        when(frozenClient.getContent(anyString())).thenReturn(new byte[] {1, 2});

        assertNull(service.read(token));
    }

    private String token(String url) {
        return url.substring(url.indexOf("ticket=") + "ticket=".length());
    }

    private FileDO file() {
        return new FileDO().setId(501L).setConfigId(51L).setPath("pms-storage-receipts/op-501")
                .setName("receipt.pdf").setType("application/pdf").setSize(3L);
    }
}
