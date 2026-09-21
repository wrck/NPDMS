package cn.iocoder.yudao.module.pms.engineering.service.configuration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationManualCommandReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.*;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ConfigurationCollectionServiceTest {
    ConfigurationCollectionOwnerMapper configurations = mock(ConfigurationCollectionOwnerMapper.class);
    ConfigurationCollectionMapper links = mock(ConfigurationCollectionMapper.class);
    ProjectDeviceSelectionApi devices = mock(ProjectDeviceSelectionApi.class);
    ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    PermissionApi permissions = mock(PermissionApi.class);
    CollectionTaskApi tasks = mock(CollectionTaskApi.class);
    CollectionCallbackApi callbacks = mock(CollectionCallbackApi.class);
    CollectionLogAccessApi logs = mock(CollectionLogAccessApi.class);
    CollectionDispatchApi dispatch = mock(CollectionDispatchApi.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    ConfigurationCollectionService service;
    ConfigurationDO configuration;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        ObjectProvider<CollectionDispatchApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dispatch);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ConfigurationCollectionService(configurations, links, devices, scopes, permissions, tasks, callbacks, logs, provider, tx);
        configuration = new ConfigurationDO(); configuration.setId(11L); configuration.setTenantId(1L);
        configuration.setProjectId(21L); configuration.setEquipmentId(31L); configuration.setVersion(0); configuration.setStatus(1);
        when(configurations.lockById(11L)).thenReturn(configuration); when(configurations.selectById(11L)).thenReturn(configuration);
        when(permissions.hasAnyPermissions(eq(7L), any(String.class))).thenReturn(true);
        var scope = new ProjectScopeResult(21L, 1L, Set.of(21L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
        when(devices.validateSelection(21L, List.of(31L))).thenReturn(List.of(new SelectedProjectDevice(31L,"sn","device",null,null,null)));
        when(tasks.createBatch(any())).thenReturn(new CollectionBatchDTO(10L,"batch","IMP","Configuration","11","key","CREATED",1,List.of(task("CREATED"))));
        when(tasks.getTask(1L,"task")).thenReturn(task("DISPATCHED"));
        when(links.insert(any(ConfigurationCollectionDO.class))).thenAnswer(call -> { ((ConfigurationCollectionDO) call.getArgument(0)).setId(41L); return 1; });
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void displayedCommandsAreTheFrozenDispatchSnapshotAndSurviveReplay() {
        var r=request(); r.setCommands("show version\r\n\r\nshow run\rshow tech");
        var result=service.submit(11L,7L,r);
        assertEquals("show version\nshow run\nshow tech",result.commandText());
        var saved=org.mockito.ArgumentCaptor.forClass(ConfigurationCollectionDO.class);
        verify(links).insert(saved.capture());
        var sent=org.mockito.ArgumentCaptor.forClass(CollectionDispatchApi.Command.class);
        verify(dispatch).dispatchManual(sent.capture());
        assertEquals(String.join("\n",sent.getValue().commands()),saved.getValue().getCommandText());
        when(links.findRequest(1L,r.getRequestKey())).thenReturn(saved.getValue());
        var replay=request(); replay.setCommands(r.getCommands());
        assertEquals(result.commandText(),service.submit(11L,7L,replay).commandText());
        verify(dispatch,times(1)).dispatchManual(any());
        var changed=request(); changed.setCommands("show run");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.submit(11L,7L,changed));
        assertEquals(result.commandText(),saved.getValue().getCommandText());
    }
    @Test void persistsBindingBeforeRemoteExecutionAndClearsPassword() {
        var request = request();
        var result = service.submit(11L, 7L, request);
        assertEquals("DISPATCHED", result.task().status()); assertArrayEquals(new char[6], request.getPassword());
        var order = inOrder(links, tx, dispatch);
        order.verify(links).insert(any(ConfigurationCollectionDO.class)); order.verify(tx).commit(any());
        order.verify(dispatch).dispatchManual(any());
        assertEquals(1, configuration.getStatus());
    }
    @Test void duplicateRequestReturnsOriginalTaskWithoutExecutingAgain() {
        var request = request(); service.submit(11L,7L,request);
        var captor = org.mockito.ArgumentCaptor.forClass(ConfigurationCollectionDO.class);
        verify(links).insert(captor.capture()); when(links.findRequest(1L,request.getRequestKey())).thenReturn(captor.getValue());
        service.submit(11L,7L,request()); verify(dispatch,times(1)).dispatchManual(any());
    }
    @Test void failedAuthorizationCannotSendAndClearsSecret() {
        when(permissions.hasAnyPermissions(eq(7L),any(String.class))).thenReturn(false);
        var r=request(); assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.submit(11L,7L,r));
        assertArrayEquals(new char[6],r.getPassword()); verifyNoInteractions(dispatch,tasks);
    }
    @Test void crossTenantRecordCannotSend() {
        configuration.setTenantId(9L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.submit(11L,7L,request())); verifyNoInteractions(dispatch,tasks);
    }
    @Test void projectScopeCannotBeBypassed() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(),Set.of()));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.submit(11L,7L,request())); verifyNoInteractions(dispatch,tasks);
    }
    @Test void staleVersionAndCompletedRecordCannotSend() {
        configuration.setVersion(2);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.submit(11L,7L,request()));
        configuration.setVersion(0); configuration.setStatus(2);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.submit(11L,7L,request())); verifyNoInteractions(dispatch,tasks);
    }
    @Test void invalidRequestStillErasesPassword() {
        var r=request();r.setHost("https://invalid/path");
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.submit(11L,7L,r));
        assertArrayEquals(new char[6],r.getPassword());verifyNoInteractions(dispatch,tasks);
    }
    @Test void consumesMatchingResultWithoutChangingConfigurationLifecycle() {
        var link=link(); when(links.selectById(41L)).thenReturn(link); when(links.updateById(link)).thenReturn(1);
        when(tasks.getTask(1L,"task")).thenReturn(task("RESULT_AVAILABLE"));
        service.consume(11L,41L,7L);
        verify(callbacks).confirmConsumption(new CollectionConsumptionCommand("task","IMP","Configuration","11",1L,"request-key"));
        assertEquals(1L,link.getConsumedResultVersion()); assertEquals(1,configuration.getStatus());
        verify(configurations,never()).updateById(any(ConfigurationDO.class));
    }
    @Test void changedDeviceCannotConsumeHistoricalResult() {
        when(links.selectById(41L)).thenReturn(link()); configuration.setEquipmentId(99L);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.consume(11L,41L,7L));verifyNoInteractions(callbacks);
    }
    @Test void foreignExecutionCannotBeCancelled() {
        var link=link();link.setConfigurationId(99L);when(links.selectById(41L)).thenReturn(link);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.cancel(11L,41L,7L));verifyNoInteractions(dispatch);
    }
    @Test void requestLookupRequiresScopeAndCannotCrossConfiguration() {
        String key=request().getRequestKey();
        assertNull(service.findByRequestKey(11L,7L,key));
        var row=link(); row.setCommandText("show run"); when(links.findRequest(1L,key)).thenReturn(row);
        assertEquals("show run",service.findByRequestKey(11L,7L,key).commandText());
        row.setConfigurationId(12L);
        assertNull(service.findByRequestKey(11L,7L,key));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(21L,1L,Set.of(),Set.of()));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> service.findByRequestKey(11L,7L,key));
    }
    @Test void movedConfigurationCannotCancelOriginalProjectExecution() {
        var link=link(); when(links.selectById(41L)).thenReturn(link); configuration.setProjectId(22L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(22L,1L,Set.of(22L),Set.of()));
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,()->service.cancel(11L,41L,7L));
        verifyNoInteractions(dispatch);
    }
    private ConfigurationCollectionDO link() {
        var l=new ConfigurationCollectionDO();l.setId(41L);l.setTenantId(1L);l.setConfigurationId(11L);l.setEquipmentId(31L);l.setPlatformTaskId("task");l.setRequestKey("request-key");return l;
    }
    private ConfigurationManualCommandReqVO request() {
        var r=new ConfigurationManualCommandReqVO();r.setRequestKey("01234567-0123-0123-0123-012345678901");r.setExpectedVersion(0);
        r.setHost("127.0.0.1");r.setPort(22);r.setProtocol("SSH");r.setUsername("operator");r.setPassword("secret".toCharArray());r.setCommands("show version");return r;
    }
    private CollectionTaskDTO task(String status) {
        return new CollectionTaskDTO(1L,10L,"task","IMP","Configuration","11","21","31","device","127.0.0.1",22,"SSH",
                "manual-11","v1","hash","TEMPORARY_SECRET",null,null,"key","BUSINESS_CONSUMPTION",status,"ACCEPTED",1L,51L,"IMP","Configuration","11",null,null);
    }
}
