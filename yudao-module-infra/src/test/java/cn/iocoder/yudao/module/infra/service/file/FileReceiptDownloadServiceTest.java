package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.infra.controller.admin.file.FileController;
import cn.iocoder.yudao.module.infra.controller.admin.file.FileStorageReceiptContentController;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.*;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileReceiptDownloadServiceTest {
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String,String> values=mock(ValueOperations.class);
    private final FileMapper files=mock(FileMapper.class);
    private final FileConfigService configs=mock(FileConfigService.class);
    private final FileClient client=mock(FileClient.class);
    private final Map<String,String> cache=new HashMap<>();
    private final FileReceiptDownloadService service=new FileReceiptDownloadService(redis,files,configs,new WebProperties());
    private final FileDO file=FileDO.builder().id(7L).configId(36L).path("pms-storage-receipts/test")
            .name("设备日志.txt").type("text/plain").size(3L).build();
    @BeforeEach void setup() throws Exception {
        var request=new MockHttpServletRequest();request.setServerName("10.210.0.11");request.setServerPort(59191);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call->{cache.put(call.getArgument(0),call.getArgument(1));return null;})
                .when(values).set(anyString(),anyString(),any(Duration.class));
        when(values.get(anyString())).thenAnswer(call->cache.get(call.getArgument(0)));
        when(files.selectById(7L)).thenReturn(file);
        when(configs.getFileClient(36L)).thenReturn(client);
        when(client.getContent(file.getPath())).thenReturn(new byte[]{1,2,3});
    }
    @AfterEach void cleanup(){RequestContextHolder.resetRequestAttributes();}
    private String token(){return service.issue(file,60).split("ticket=",2)[1];}
    @Test void issuesBoundExpiringTicketAndReturnsExactContent() throws Exception {
        String token=token();
        assertFalse(cache.keySet().iterator().next().contains(token));
        assertFalse(cache.values().iterator().next().contains(token));
        verify(values).set(anyString(),anyString(),eq(Duration.ofSeconds(60)));
        assertArrayEquals(new byte[]{1,2,3},service.read(token).content());
        var response=new MockHttpServletResponse();
        new FileStorageReceiptContentController(mock(FileStorageReceiptAccessService.class), service).content(token,response);
        assertEquals(200,response.getStatus());
        assertEquals("no-store",response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("attachment;"));
        assertArrayEquals(new byte[]{1,2,3},response.getContentAsByteArray());
    }
    @Test void absentForgedAndExpiredTicketsFailClosed() throws Exception {
        assertNull(service.read(null));assertNull(service.read("invalid"));assertNull(service.read("a".repeat(43)));
        String token=token();String key=FileReceiptDownloadService.key(token);
        cache.put(key,JsonUtils.toJsonString(new FileReceiptDownloadService.Binding(7L,36L,file.getPath(),0)));
        assertNull(service.read(token));cache.clear();assertNull(service.read(token));
        var response=new MockHttpServletResponse();new FileStorageReceiptContentController(mock(FileStorageReceiptAccessService.class), service).content(token,response);
        assertEquals(404,response.getStatus());verify(client,never()).getContent(anyString());
    }
    @Test void changedOrDeletedFileCannotReuseAnIssuedTicket() throws Exception {
        String token=token();file.setConfigId(999L);assertNull(service.read(token));
        file.setConfigId(36L);file.setPath("other");assertNull(service.read(token));
        when(files.selectById(7L)).thenReturn(null);assertNull(service.read(token));
        verify(client,never()).getContent(anyString());
    }
    @Test void rejectsInvalidExpirationAndUnmanagedPaths() {
        assertThrows(IllegalArgumentException.class,()->service.issue(file,0));
        file.setPath("public/a.txt");assertThrows(IllegalArgumentException.class,()->service.issue(file,60));
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void legacyPublicDownloadCannotBypassReceiptTicket() throws Exception {
        var legacy=new FileController();var legacyFiles=mock(FileService.class);
        ReflectionTestUtils.setField(legacy,"fileService",legacyFiles);
        for(String path:List.of("pms-storage-receipts/test","PMS-STORAGE-RECEIPTS/test","pms-storage-receipts%2Ftest")) {
            var request=new MockHttpServletRequest("GET","/admin-api/infra/file/36/get/"+path);
            var response=new MockHttpServletResponse();legacy.getFileContent(request,response,36L);
            assertEquals(404,response.getStatus());
        }
        verifyNoInteractions(legacyFiles);
    }
    @Test void legacyAliasesAreCheckedAgainstTheStoredPathBeforeContentRead() throws Exception {
        var legacy=new FileController();var legacyFiles=mock(FileService.class);
        ReflectionTestUtils.setField(legacy,"fileService",legacyFiles);
        String alias="pmś-storage-receipts/test";
        when(legacyFiles.getFileByConfigIdAndPath(36L,alias)).thenReturn(file);
        var request=new MockHttpServletRequest("GET","/admin-api/infra/file/36/get/"+alias);
        var response=new MockHttpServletResponse();legacy.getFileContent(request,response,36L);
        assertEquals(404,response.getStatus());
        verify(legacyFiles,never()).getFileContent(anyLong(),anyString());
    }
}
