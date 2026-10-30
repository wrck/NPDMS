package cn.iocoder.yudao.module.pms.platform.service.file;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerScopedFileContentServiceTest {
    private final OwnerScopedFileReadPolicy policy = mock(OwnerScopedFileReadPolicy.class);
    private final FileEvidenceApi evidence = mock(FileEvidenceApi.class);
    private final FileVersionMapper versions = mock(FileVersionMapper.class);
    private final FileStorageContentApi storage = mock(FileStorageContentApi.class);
    private final OwnerScopedFileContentService service = new OwnerScopedFileContentService(List.of(policy), evidence, versions, storage);
    private final OwnerScopedFileContentApi.Query query = new OwnerScopedFileContentApi.Query("SOL_REVIEW","SOL_REVIEW:1:42",80L);
    private FileVersionDO version;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L),new MockHttpServletRequest());
        when(policy.ownerCode()).thenReturn("SOL_REVIEW");
        when(evidence.inspectDocument(1L,80L)).thenReturn(new FileEvidenceApi.Document(80L,"PLT","DELIVERY_MATERIAL","SOL:solution:42","IMPLEMENTATION_PLAN","script",90L,1,"digest","config.txt",true));
        when(evidence.lockAndRevalidate(any())).thenReturn(new FileEvidenceApi.Fact(true,null,1,1,1));
        version = new FileVersionDO(); version.setInfraFileId(100L); version.setSizeBytes(4L); version.setSha256("digest");
        version.setAvailabilityStatusCode("AVAILABLE"); version.setScanStatusCode("PASSED"); version.setDetectedMediaType("text/plain");
        when(versions.selectForUpdate(any())).thenReturn(version);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void exactAvailableFileIsReadAndOwnerIsRecheckedBeforeReturning() {
        when(storage.read(any())).thenReturn(new byte[]{1,2,3,4});
        assertArrayEquals(new byte[]{1,2,3,4},service.read(query).bytes());
        var order = inOrder(policy,evidence,versions,storage);
        order.verify(policy).require(any()); order.verify(evidence).lockAndRevalidate(any());
        order.verify(versions).selectForUpdate(any()); order.verify(storage).read(any()); order.verify(policy).require(any());
    }
    @Test void revokedOwnerDuringReadNeverReturnsContent() {
        doNothing().doThrow(new SecurityException("revoked")).when(policy).require(any());
        when(storage.read(any())).thenReturn(new byte[]{1});
        assertThrows(SecurityException.class, () -> service.read(query));
        verify(policy,times(2)).require(any());
    }
    @Test void unavailableOrReplacedFileNeverReachesStorage() {
        version.setSha256("changed");
        assertThrows(RuntimeException.class, () -> service.read(query)); verifyNoInteractions(storage);
        version.setSha256("digest"); version.setScanStatusCode("FAILED");
        assertThrows(RuntimeException.class, () -> service.read(query)); verifyNoInteractions(storage);
    }
    @Test void ownerRejectionNeverReadsStorage() {
        doThrow(new SecurityException("outside task")).when(policy).require(any());
        assertThrows(SecurityException.class, () -> service.read(query)); verifyNoInteractions(storage,versions);
    }
}
