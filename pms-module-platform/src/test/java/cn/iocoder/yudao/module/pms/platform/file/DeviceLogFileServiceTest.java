package cn.iocoder.yudao.module.pms.platform.file;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileStorageContentApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.platform.api.file.DeviceLogFileApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import cn.iocoder.yudao.module.pms.platform.service.file.*;
import cn.iocoder.yudao.module.pms.platform.service.delivery.DeliveryMaterialService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeviceLogFileServiceTest {
    final CollectionTaskMapper tasks=mock(CollectionTaskMapper.class);
    final CollectionRequestMapper requests=mock(CollectionRequestMapper.class);
    final FileVersionMapper versions=mock(FileVersionMapper.class);
    final FileStorageContentApi storage=mock(FileStorageContentApi.class);
    final CollectionSourceAdapter source=mock(CollectionSourceAdapter.class);
    final CollectionTaskDO task=new CollectionTaskDO();
    final DeviceLogFileApi.Origin origin=new DeviceLogFileApi.Origin(1L,7L,10L,20L,"task",1L,100L);
    DeviceLogFileService service;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        TenantContextHolder.setTenantId(1L);
        ObjectProvider<CollectionSourceAdapter> sources=mock(ObjectProvider.class);
        when(sources.orderedStream()).thenAnswer(inv->Stream.of(source));when(source.entry()).thenReturn("configuration");
        service=new DeviceLogFileService(tasks,requests,sources,versions,storage,mock(FileBusinessObjectPolicyRegistry.class),
                mock(FileUploadApplicationService.class),mock(DeliveryMaterialService.class),mock(PermissionApi.class));
        task.setSourceContext("IMP");task.setSourceObjectType("Configuration");task.setSourceObjectId("30");
        task.setProjectId("10");task.setDeviceId("20");task.setStatus("RESULT_AVAILABLE");task.setExternalStatus("SUCCEEDED");
        task.setResultVersion(1L);task.setFileVersionId(100L);
        when(tasks.selectByTenantAndPlatformTaskId(1L,"task")).thenReturn(task);
        var request=new CollectionRequestDO();request.setEntry("configuration");request.setObjectId(30L);request.setActorId(7L);
        when(requests.findByTask(1L,"task")).thenReturn(request);
        when(source.authorize(1L,7L,30L,20L,CollectionSourceAdapter.Access.CONSUME,null)).thenReturn(new CollectionSourceAdapter.Source(
                "configuration",30L,"IMP","Configuration",10L,20L,"device",1L,true,true,"BUSINESS_CONSUMPTION","configuration"));
        var version=new FileVersionDO();version.setInfraFileId(200L);version.setSizeBytes(10L);version.setSha256("a".repeat(64));
        version.setAvailabilityStatusCode("AVAILABLE");version.setScanStatusCode("SKIPPED");when(versions.selectByTenantAndId(1L,100L)).thenReturn(version);
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void failedOrChangedTaskNeverReadsStorage() {
        task.setStatus("FAILED");assertThrows(IllegalStateException.class,()->service.readCollection(origin));
        task.setStatus("RESULT_AVAILABLE");task.setDeviceId("21");assertThrows(IllegalStateException.class,()->service.readCollection(origin));
        verifyNoInteractions(storage);
    }
    @Test void currentOwnerMustStillAuthorizeOriginalProjectAndDevice() {
        when(source.authorize(anyLong(),anyLong(),anyLong(),anyLong(),any(),isNull())).thenReturn(new CollectionSourceAdapter.Source(
                "configuration",30L,"IMP","Configuration",11L,20L,"device",2L,true,true,"BUSINESS_CONSUMPTION","configuration"));
        assertThrows(IllegalStateException.class,()->service.readCollection(origin));verifyNoInteractions(storage);
    }
    @Test void successfulAuthorizedResultReadsItsExactVersion() {
        byte[] bytes=new byte[10];when(storage.read(any())).thenReturn(bytes);
        assertSame(bytes,service.readCollection(origin));
        verify(storage).read(new FileStorageContentApi.Query(1L,200L,10L,"a".repeat(64),20*1024*1024));
        verify(source).authorize(1L,7L,30L,20L,CollectionSourceAdapter.Access.CONSUME,null);
    }
    @Test void foreignTenantCannotInspectTask() {
        assertThrows(IllegalArgumentException.class,()->service.readCollection(new DeviceLogFileApi.Origin(2L,7L,10L,20L,"task",1L,100L)));
        verifyNoInteractions(storage);verify(tasks,never()).selectByTenantAndPlatformTaskId(anyLong(),anyString());
    }
}
