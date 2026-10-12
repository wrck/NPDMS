package cn.iocoder.yudao.module.pms.platform.file;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.delivery.DeliveryMaterialDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.service.delivery.*;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NativeLogMaterialDownloadTest {
    final DeliveryMaterialMapper materials=mock(DeliveryMaterialMapper.class);
    final FileEvidenceApi evidence=mock(FileEvidenceApi.class);
    final FileAccessTicketService tickets=mock(FileAccessTicketService.class);
    final DeliveryOwnerAccess owners=mock(DeliveryOwnerAccess.class);
    final DeliveryDocumentOriginResolver origins=mock(DeliveryDocumentOriginResolver.class);
    final NativeGeneratedFileService service=new NativeGeneratedFileService(mock(FileBusinessObjectPolicyRegistry.class),mock(FileUploadApplicationService.class),mock(DeliveryMaterialService.class),materials,evidence,tickets,mock(PermissionApi.class),owners,origins);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);var user=new LoginUser();user.setId(7L);user.setTenantId(1L);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null));
        var m=new DeliveryMaterialDO();m.setId(40L);m.setTenantId(1L);m.setOwnerModule("AST");m.setEntityType("device-log-file");m.setEntityId(20L);m.setProjectId(10L);m.setTypeCode("AST.DEVICE_CONFIGURATION_LOG");m.setStatus("ACTIVE");m.setMaterialKind("FILE");m.setFileReferenceId(30L);m.setFileArtifactId(50L);m.setFileVersionNo(1);m.setFileSha256("digest");
        when(materials.selectById(40L)).thenReturn(m);when(materials.selectMaterialsForUpdate(any())).thenReturn(List.of(m));
        when(evidence.inspectDocument(1L,30L)).thenReturn(new FileEvidenceApi.Document(30L,"AST","DeviceConfigurationLogFile","20","DEVICE_CONFIGURATION_LOG","key",50L,1,"digest","log.txt",true));
        when(tickets.create(any())).thenReturn(new cn.iocoder.yudao.module.pms.platform.controller.admin.file.vo.FileAccessTicketRespVO(1L,"https://fixture.invalid/authorized",java.time.LocalDateTime.now().plusMinutes(1)));
        when(origins.resolve(any())).thenReturn(new FileDocumentSourceProvider.Scope(10L,"AST.DEVICE_CONFIGURATION_LOG","AST","device-log-file",20L,1L));
    }
    @AfterEach void clear() {TenantContextHolder.clear();SecurityContextHolder.clearContext();}
    @Test void declaredFileAliasCanPassOnlyAfterOwnerAndMaterialLocks() {
        service.requestDownload("AST","device-log-file",20L,40L);
        var order=inOrder(owners,materials,evidence,tickets);order.verify(owners).require("AST","device-log-file",20L,"AST.DEVICE_CONFIGURATION_LOG",false,true);order.verify(materials).selectMaterialsForUpdate(any());order.verify(evidence).inspectDocument(1L,30L);order.verify(tickets).create(any());
    }
    @Test void differentProjectOrOwnerDoesNotGrantTicket() {
        when(origins.resolve(any())).thenReturn(new FileDocumentSourceProvider.Scope(11L,"AST.DEVICE_CONFIGURATION_LOG","AST","device-log-file",20L,1L));assertThrows(RuntimeException.class,()->service.requestDownload("AST","device-log-file",20L,40L));verifyNoInteractions(tickets);
        when(origins.resolve(any())).thenReturn(new FileDocumentSourceProvider.Scope(10L,"AST.DEVICE_CONFIGURATION_LOG","AST","device-log-file",21L,1L));assertThrows(RuntimeException.class,()->service.requestDownload("AST","device-log-file",20L,40L));verifyNoInteractions(tickets);
    }
    @Test void deniedOwnerCannotReachMaterialLockOrTicket() {
        doThrow(new IllegalStateException("denied")).when(owners).require(anyString(),anyString(),anyLong(),anyString(),anyBoolean(),anyBoolean());assertThrows(RuntimeException.class,()->service.requestDownload("AST","device-log-file",20L,40L));verify(materials,never()).selectMaterialsForUpdate(any());verifyNoInteractions(tickets);
    }
}
