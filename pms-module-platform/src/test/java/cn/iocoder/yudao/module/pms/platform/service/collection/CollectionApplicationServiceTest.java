package cn.iocoder.yudao.module.pms.platform.service.collection;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsResourceApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CollectionApplicationServiceTest {
    CollectionSourceAdapter source=mock(CollectionSourceAdapter.class);
    CollectionRequestMapper requests=mock(CollectionRequestMapper.class);
    CollectionTaskMapper taskMapper=mock(CollectionTaskMapper.class);
    CollectionTaskApi tasks=mock(CollectionTaskApi.class);
    CollectionBusinessResultDeliveryService callbacks=mock(CollectionBusinessResultDeliveryService.class);
    CollectionLogAccessApi logs=mock(CollectionLogAccessApi.class);
    CollectionTemplateService templates=mock(CollectionTemplateService.class);
    CollectionConnectionService connections=mock(CollectionConnectionService.class);
    ProjectDeviceSelectionApi devices=mock(ProjectDeviceSelectionApi.class);
    CollectionDispatchApi dispatch=mock(CollectionDispatchApi.class);
    PlatformTransactionManager tx=mock(PlatformTransactionManager.class);
    CollectionApplicationService service;
    CollectionSourceAdapter.Source context;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);var provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(dispatch);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service=new CollectionApplicationService(List.of(source),requests,taskMapper,tasks,callbacks,logs,templates,connections,devices,provider,tx);
        context=new CollectionSourceAdapter.Source("configuration",11L,"IMP","Configuration",21L,31L,"device",0L,true,true,"BUSINESS_CONSUMPTION","配置调试");
        when(source.entry()).thenReturn("configuration");when(source.authorize(any(),any(),any(),any(),any(),any())).thenAnswer(call->context);
        when(devices.validateSelection(21L,List.of(31L))).thenReturn(List.of(new SelectedProjectDevice(31L,"sn","device",null,"model-a",null)));
        when(tasks.createBatch(any())).thenReturn(new CollectionBatchDTO(10L,"batch","IMP","Configuration","11","key","CREATED",1,List.of(task("CREATED"))));
        when(tasks.getTask(1L,"task")).thenReturn(task("DISPATCHED"));
        when(taskMapper.selectByTenantAndPlatformTaskId(1L,"task")).thenReturn(new CollectionTaskDO());
        when(requests.insert(any(CollectionRequestDO.class))).thenAnswer(call->{call.<CollectionRequestDO>getArgument(0).setId(41L);return 1;});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void freezesCommandsBeforeIoAndReplaysWithoutDispatch(){
        var r=request();r.setCommands("show version\r\n\nshow run");var result=service.submit("configuration",11L,7L,r);
        assertArrayEquals(new char[6],r.getPassword());assertEquals("show version\nshow run",result.commandText());
        var ordered=inOrder(requests,tx,dispatch);ordered.verify(requests).insert(any(CollectionRequestDO.class));ordered.verify(tx).commit(any());ordered.verify(dispatch).dispatchManual(any());
        var row=saved();when(requests.findRequest(1L,r.getRequestKey())).thenReturn(row);
        var replay=request();replay.setCommands("show version\nshow run");service.submit("configuration",11L,7L,replay);
        verify(dispatch,times(1)).dispatchManual(any());
        var changed=request();changed.setCommands("show tech");assertThrows(IllegalStateException.class,()->service.submit("configuration",11L,7L,changed));
    }
    @Test void savedConnectionUsesPublishedServerCommandsAndNoTemporarySecret(){
        var t=new CollectionTemplateDO();t.setId(50L);t.setName("Published");t.setRevision(2);t.setCommandText("show run");
        when(templates.forExecution(7L,50L,"configuration","SSH","model-a")).thenReturn(t);
        var credential=new DeviceCredentialDO();credential.setId(60L);
        when(connections.resolve(7L,60L,21L,31L,"SSH",50L)).thenReturn(new CollectionConnectionService.Resolved(credential,70L,new DeviceOpsResourceApi.Connection("dac-ref","stored.example",5555,"SSH","saved-user",4)));
        var r=request();r.setTemplateId(50L);r.setCredentialId(60L);r.setPassword(null);r.setCommands("untrusted client command");
        assertEquals("show run",service.submit("configuration",11L,7L,r).commandText());
        var sent=ArgumentCaptor.forClass(CollectionDispatchApi.SavedCommand.class);verify(dispatch).dispatchSaved(sent.capture());
        assertEquals(List.of("show run"),sent.getValue().commands());assertEquals("dac-ref",sent.getValue().connectionId());assertEquals(4L,sent.getValue().connectionVersion());
        verify(dispatch,never()).dispatchManual(any());
    }
    @Test void savedConnectionExecutesManualCommandsWithoutTemplate() {
        var credential=new DeviceCredentialDO();credential.setId(60L);
        when(connections.resolve(7L,60L,21L,31L,"SSH",null)).thenReturn(new CollectionConnectionService.Resolved(credential,70L,new DeviceOpsResourceApi.Connection("dac-ref","stored.example",5555,"SSH","saved-user",4)));
        var r=request();r.setCredentialId(60L);r.setPassword(null);r.setCommands("show version\nshow run\nshow tech");
        assertEquals(r.getCommands(),service.submit("configuration",11L,7L,r).commandText());
        var sent=ArgumentCaptor.forClass(CollectionDispatchApi.SavedCommand.class);verify(dispatch).dispatchSaved(sent.capture());
        assertEquals(List.of("show version","show run","show tech"),sent.getValue().commands());
        assertNull(saved().getTemplateRevisionId());verifyNoInteractions(templates);
    }
    @Test void registeredJointTestCanExecuteManualCommands() {
        context=new CollectionSourceAdapter.Source("joint-test",11L,"IMP","JointTest",21L,31L,"device",0L,true,true,"BUSINESS_CONSUMPTION","业务联调");
        when(source.entry()).thenReturn("joint-test");
        service.submit("joint-test",11L,7L,request());verify(dispatch).dispatchManual(any());verifyNoInteractions(templates);
    }
    @Test void concurrentWinnerDiscoveredAfterSourceLockIsReturnedWithoutRedispatch(){
        service.submit("configuration",11L,7L,request());var row=saved();clearInvocations(dispatch,requests,tasks);
        when(requests.lockRequest(1L,request().getRequestKey())).thenReturn(row);
        var winner=new CollectionTaskDO();winner.setSourceObjectId("11");winner.setProjectId("21");winner.setSourceContext("IMP");winner.setSourceObjectType("Configuration");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(1L,"task")).thenReturn(winner);
        assertEquals(41L,service.submit("configuration",11L,7L,request()).id());
        verify(dispatch,never()).dispatchManual(any());verify(tasks,never()).createBatch(any());
    }
    @Test void sourceRestrictionAndUnregisteredEntrypointsStillRejectManualCommands(){
        context=new CollectionSourceAdapter.Source("joint-test",11L,"IMP","JointTest",21L,31L,"device",0L,false,true,"BUSINESS_CONSUMPTION","业务联调");
        when(source.entry()).thenReturn("joint-test");
        assertThrows(CollectionOperationException.class,()->service.submit("joint-test",11L,7L,request()));
        for(String entry:List.of("cutover","inspection"))assertThrows(CollectionOperationException.class,()->service.submit(entry,11L,7L,request()));
        verifyNoInteractions(dispatch,tasks);
    }
    @Test void sourceAuthorizationFailureClearsSecretAndDoesNotDispatch(){
        when(source.authorize(any(),any(),any(),any(),any(),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        var r=request();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.submit("configuration",11L,7L,r));
        assertArrayEquals(new char[6],r.getPassword());verifyNoInteractions(dispatch,tasks);
    }
    @Test void movedProjectCannotReplayReadCancelOrConsumeHistory(){
        service.submit("configuration",11L,7L,request());var row=saved();when(requests.findRequest(1L,request().getRequestKey())).thenReturn(row);when(requests.selectById(41L)).thenReturn(row);clearInvocations(dispatch);
        context=new CollectionSourceAdapter.Source("configuration",11L,"IMP","Configuration",22L,31L,"device",0L,true,true,"BUSINESS_CONSUMPTION","配置调试");
        assertThrows(IllegalStateException.class,()->service.submit("configuration",11L,7L,request()));
        assertThrows(IllegalStateException.class,()->service.findByRequestKey("configuration",11L,7L,request().getRequestKey()));
        assertThrows(IllegalStateException.class,()->service.cancel("configuration",11L,7L,41L));
        assertThrows(IllegalStateException.class,()->service.consume("configuration",11L,7L,41L));verifyNoInteractions(dispatch,callbacks);
    }
    private CollectionRequestDO saved(){var c=ArgumentCaptor.forClass(CollectionRequestDO.class);verify(requests).insert(c.capture());return c.getValue();}
    private CollectionExecutionRequest request(){var r=new CollectionExecutionRequest();r.setRequestKey("01234567-0123-0123-0123-012345678901");r.setExpectedVersion(0L);r.setHost("device.example");r.setPort(22);r.setProtocol("SSH");r.setUsername("operator");r.setPassword("secret".toCharArray());r.setCommands("show version");return r;}
    private CollectionTaskDTO task(String status){return new CollectionTaskDTO(1L,10L,"task","IMP","Configuration","11","21","31","device","device.example",22,"SSH","manual-11","v1","hash","TEMPORARY_SECRET",null,null,"key","BUSINESS_CONSUMPTION",status,"ACCEPTED",1L,51L,"IMP","Configuration","11",null,null);}
}
