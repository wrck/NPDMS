package cn.iocoder.yudao.module.infra.service.file;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FileStorageContentServiceTest {
    final FileService files=mock(FileService.class);
    final FileStorageContentService service=new FileStorageContentService(files);
    final byte[] content="complete device output".getBytes(StandardCharsets.UTF_8);
    final String hash=org.apache.commons.codec.digest.DigestUtils.sha256Hex(content);
    @BeforeEach void setup() {TenantContextHolder.setTenantId(1L);}
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void readsOnlyExactRegisteredReceiptContent() throws Exception {
        var file=new FileDO().setId(10L).setConfigId(2L).setPath("pms-storage-receipts/test/content.txt").setSize((long)content.length);
        when(files.getFile(10L)).thenReturn(file);when(files.getFileContent(2L,file.getPath())).thenReturn(content);
        assertArrayEquals(content,service.read(query(1L,hash)));
        assertThrows(IllegalStateException.class,()->service.read(query(1L,"a".repeat(64))));
    }
    @Test void wrongTenantOrLegacyPathCannotReadAnyBytes() throws Exception {
        assertThrows(IllegalArgumentException.class,()->service.read(query(2L,hash)));verifyNoInteractions(files);
        when(files.getFile(10L)).thenReturn(new FileDO().setId(10L).setConfigId(2L).setPath("legacy/device.log").setSize((long)content.length));
        assertThrows(IllegalStateException.class,()->service.read(query(1L,hash)));
        verify(files,never()).getFileContent(anyLong(),anyString());
    }
    @Test void authorizedFiftyMegabyteLimitAlsoReadsSmallInspectionFiles() throws Exception {
        var file=new FileDO().setId(10L).setConfigId(2L).setPath("pms-storage-receipts/inspection/log.json").setSize((long)content.length);
        when(files.getFile(10L)).thenReturn(file);when(files.getFileContent(2L,file.getPath())).thenReturn(content);
        assertArrayEquals(content,service.read(new FileStorageContentApi.Query(1L,10L,(long)content.length,hash,50*1024*1024)));
    }
    @Test void readsReceiptAtPlatformFiftyMegabyteBoundary() throws Exception {
        byte[] boundary=new byte[50*1024*1024];
        boundary[boundary.length-1]=1;
        String digest=org.apache.commons.codec.digest.DigestUtils.sha256Hex(boundary);
        var file=new FileDO().setId(10L).setConfigId(2L).setPath("pms-storage-receipts/inspection/large.log").setSize((long)boundary.length);
        when(files.getFile(10L)).thenReturn(file);when(files.getFileContent(2L,file.getPath())).thenReturn(boundary);
        assertSame(boundary,service.read(new FileStorageContentApi.Query(1L,10L,(long)boundary.length,digest,boundary.length)));
    }
    @Test void platformMaximumAndSmallerCallerLimitRemainEnforcedBeforeStorageRead() {
        assertThrows(IllegalArgumentException.class,()->service.read(new FileStorageContentApi.Query(
                1L,10L,(long)content.length,hash,50*1024*1024+1)));
        assertThrows(IllegalArgumentException.class,()->service.read(new FileStorageContentApi.Query(
                1L,10L,20*1024*1024+1L,hash,20*1024*1024)));
        verifyNoInteractions(files);
    }
    private FileStorageContentApi.Query query(long tenant,String digest) {return new FileStorageContentApi.Query(tenant,10L,(long)content.length,digest,1024);}
}
