package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.configurationlog.DeviceLogArchiveService;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceConfigurationLogImportApiImplTest {
    final DeviceLogArchiveService archive = mock(DeviceLogArchiveService.class);
    final DeviceConfigurationLogImportApiImpl api = new DeviceConfigurationLogImportApiImpl(archive);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(7L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    DeviceConfigurationLogImportApi.Command command(Long tenant, Long actor) {
        return new DeviceConfigurationLogImportApi.Command(tenant,actor,21L,"request","log.txt",new byte[]{1});
    }
    DeviceLogArchiveService.FileView file(boolean available) {
        return new DeviceLogArchiveService.FileView(100L,31L,"SN-1",1L,"MANUAL_UPLOAD","log.txt",50L,60L,null,available);
    }
    @Test void delegatesToExistingArchiveAndReturnsOnlyFileReferences() {
        when(archive.upload(any())).thenReturn(new DeviceLogArchiveService.ImportView(50L,"ARCHIVED",null,1,0,List.of(file(true))));
        var result = api.importLog(command(1L,7L));
        assertEquals(100L,result.files().getFirst().fileId()); assertEquals("SN-1",result.files().getFirst().serialNumber());
        verify(archive).upload(argThat(command -> command.projectId().equals(21L) && command.operationId().equals("request") && command.fileName().equals("log.txt")));
    }
    @Test void wrongTenantDoesNotReachArchive() {
        assertThrows(IllegalArgumentException.class,()->api.importLog(command(2L,7L))); verifyNoInteractions(archive);
    }
    @Test void sourceArchiveIsPreservedButDoesNotCreateADeviceConfiguration() {
        var source = new DeviceLogArchiveService.FileView(99L,null,null,null,"MANUAL_UPLOAD","log.source.txt",50L,59L,null,true);
        when(archive.upload(any())).thenReturn(new DeviceLogArchiveService.ImportView(50L,"ARCHIVED",null,1,0,List.of(source,file(true))));
        var result=api.importLog(command(1L,7L));
        assertEquals(1,result.files().size());
        assertEquals(31L,result.files().getFirst().deviceId());
        assertEquals(100L,result.files().getFirst().fileId());
    }
    @Test void wrongActorDoesNotReachArchive() {
        assertThrows(IllegalArgumentException.class,()->api.importLog(command(1L,8L))); verifyNoInteractions(archive);
    }
    @Test void unavailableOrDifferentFileCannotCompleteConfiguration() {
        var q=new DeviceConfigurationLogImportApi.FileQuery(1L,7L,21L,31L,100L);
        when(archive.history(21L,31L)).thenReturn(List.of(file(false)));
        assertThrows(IllegalStateException.class,()->api.validateFile(q));
        when(archive.history(21L,31L)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->api.validateFile(q));
    }
    @Test void availableFileUsesCurrentProjectDeviceHistory() {
        when(archive.history(21L,31L)).thenReturn(List.of(file(true)));
        api.validateFile(new DeviceConfigurationLogImportApi.FileQuery(1L,7L,21L,31L,100L));
        verify(archive).history(21L,31L);
    }
    @Test void ownerScopeRejectionIsPreserved() {
        when(archive.history(any(),any())).thenThrow(new IllegalStateException("设备不属于当前项目"));
        assertThrows(IllegalStateException.class,()->api.validateFile(new DeviceConfigurationLogImportApi.FileQuery(1L,7L,21L,31L,100L)));
    }
}
