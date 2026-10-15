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
        context=CollectionSourceAdapter.Source.builder()
                .entry("configuration")
                .objectId(11L)
                .sourceContext("IMP")
                .sourceObjectType("Configuration")
                .projectId(21L)
                .deviceId(31L)
                .deviceName("device")
                .version(0L)
                .manualAllowed(true)
                .canExecute(true)
                .completionMode("BUSINESS_CONSUMPTION")
                .title("配置调试")
                .build();
        when(source.entry()).thenReturn("configuration");when(source.authorize(any(CollectionSourceAdapter.AuthorizationQuery.class))).thenAnswer(call->context);
        when(devices.validateSelection(21L,List.of(31L))).thenReturn(List.of(new SelectedProjectDevice(31L,"sn","device",null,"model-a",null)));
        when(tasks.createBatch(any())).thenReturn(new CollectionBatchDTO(10L,"batch","IMP","Configuration","11","key","CREATED",1,List.of(task("CREATED"))));
        when(tasks.getTask(1L,"task")).thenReturn(task("DISPATCHED"));
        when(taskMapper.selectByTenantAndPlatformTaskId(1L,"task")).thenReturn(new CollectionTaskDO());
        when(requests.insert(any(CollectionRequestDO.class))).thenAnswer(call->{call.<CollectionRequestDO>getArgument(0).setId(41L);return 1;});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void freezesCommandsBeforeIoAndReplaysWithoutDispatch(){
        var r=request();r.setCommands("show version\r\n\nshow run");var result=service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), r);
        assertArrayEquals(new char[6],r.getPassword());assertEquals("show version\nshow run",result.commandText());
        var ordered=inOrder(requests,tx,dispatch);ordered.verify(requests).insert(any(CollectionRequestDO.class));ordered.verify(tx).commit(any());ordered.verify(dispatch).dispatchManual(any());
        var row=saved();when(requests.findRequest(1L,r.getRequestKey())).thenReturn(row);
        var replay=request();replay.setCommands("show version\nshow run");service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), replay);
        verify(dispatch,times(1)).dispatchManual(any());
        var changed=request();changed.setCommands("show tech");assertThrows(IllegalStateException.class,()->service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), changed));
    }
    @Test void sameTemplateUsesTheSelectedConnectionProtocolForEachExecution(){
        var template=new CollectionTemplateDO();template.setId(50L);template.setName("Published");
        template.setRevision(2);template.setCommandText("show run");template.setProtocol("SSH");
        var query=CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(50L).entry("configuration").deviceModel("model-a").build();
        when(templates.forExecution(query)).thenReturn(template);
        for(var protocol:List.of("SSH","TELNET")) {
            var command=request();command.setRequestKey(UUID.randomUUID().toString());
            command.setTemplateId(50L);command.setProtocol(protocol);command.setPort("SSH".equals(protocol)?22:23);
            assertEquals("show run",service.submit(sourceQuery(),command).commandText());
        }
        var batches=ArgumentCaptor.forClass(CollectionBatchCreateCommand.class);
        verify(tasks,times(2)).createBatch(batches.capture());
        assertEquals(List.of("SSH","TELNET"),batches.getAllValues().stream().map(batch->batch.tasks().getFirst().protocol()).toList());
        assertTrue(batches.getAllValues().stream().allMatch(batch->"50".equals(batch.tasks().getFirst().templateId())));
        verify(templates,times(2)).forExecution(query);
    }
    @Test void savedConnectionUsesPublishedServerCommandsAndNoTemporarySecret(){
        var t=new CollectionTemplateDO();t.setId(50L);t.setName("Published");t.setRevision(2);t.setCommandText("show run");
        when(templates.forExecution(CollectionTemplateService.ExecutionQuery.builder().actorId(7L).templateId(50L).entry("configuration").deviceModel("model-a").build())).thenReturn(t);
        var credential=new DeviceCredentialDO();credential.setId(60L);
        when(connections.resolve(7L,60L,21L,31L,"SSH",50L)).thenReturn(new CollectionConnectionService.Resolved(credential,70L,new DeviceOpsResourceApi.Connection("dac-ref","stored.example",5555,"SSH","saved-user",4)));
        var r=request();r.setTemplateId(50L);r.setCredentialId(60L);r.setPassword(null);r.setCommands("untrusted client command");
        assertEquals("show run",service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), r).commandText());
        var sent=ArgumentCaptor.forClass(CollectionDispatchApi.SavedCommand.class);verify(dispatch).dispatchSaved(sent.capture());
        assertEquals(List.of("show run"),sent.getValue().commands());assertEquals("dac-ref",sent.getValue().connectionId());assertEquals(4L,sent.getValue().connectionVersion());
        verify(dispatch,never()).dispatchManual(any());
    }
    @Test void savedConnectionExecutesManualCommandsWithoutTemplate() {
        var credential=new DeviceCredentialDO();credential.setId(60L);
        when(connections.resolve(7L,60L,21L,31L,"SSH",null)).thenReturn(new CollectionConnectionService.Resolved(credential,70L,new DeviceOpsResourceApi.Connection("dac-ref","stored.example",5555,"SSH","saved-user",4)));
        var r=request();r.setCredentialId(60L);r.setPassword(null);r.setCommands("show version\nshow run\nshow tech");
        assertEquals(r.getCommands(),service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), r).commandText());
        var sent=ArgumentCaptor.forClass(CollectionDispatchApi.SavedCommand.class);verify(dispatch).dispatchSaved(sent.capture());
        assertEquals(List.of("show version","show run","show tech"),sent.getValue().commands());
        assertNull(saved().getTemplateRevisionId());verifyNoInteractions(templates);
    }
    @Test void registeredJointTestCanExecuteManualCommands() {
        context=CollectionSourceAdapter.Source.builder()
                .entry("joint-test")
                .objectId(11L)
                .sourceContext("IMP")
                .sourceObjectType("JointTest")
                .projectId(21L)
                .deviceId(31L)
                .deviceName("device")
                .version(0L)
                .manualAllowed(true)
                .canExecute(true)
                .completionMode("BUSINESS_CONSUMPTION")
                .title("业务联调")
                .build();
        when(source.entry()).thenReturn("joint-test");
        service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("joint-test")
                .objectId(11L)
                .actorId(7L)
                .build(), request());verify(dispatch).dispatchManual(any());verifyNoInteractions(templates);
    }
    @Test void concurrentWinnerDiscoveredAfterSourceLockIsReturnedWithoutRedispatch(){
        service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), request());var row=saved();clearInvocations(dispatch,requests,tasks);
        when(requests.lockRequest(1L,request().getRequestKey())).thenReturn(row);
        var winner=new CollectionTaskDO();winner.setSourceObjectId("11");winner.setProjectId("21");winner.setSourceContext("IMP");winner.setSourceObjectType("Configuration");
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(1L,"task")).thenReturn(winner);
        assertEquals(41L,service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), request()).id());
        verify(dispatch,never()).dispatchManual(any());verify(tasks,never()).createBatch(any());
    }
    @Test void sourceRestrictionAndUnregisteredEntrypointsStillRejectManualCommands(){
        context=CollectionSourceAdapter.Source.builder()
                .entry("joint-test")
                .objectId(11L)
                .sourceContext("IMP")
                .sourceObjectType("JointTest")
                .projectId(21L)
                .deviceId(31L)
                .deviceName("device")
                .version(0L)
                .manualAllowed(false)
                .canExecute(true)
                .completionMode("BUSINESS_CONSUMPTION")
                .title("业务联调")
                .build();
        when(source.entry()).thenReturn("joint-test");
        assertThrows(CollectionOperationException.class,()->service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("joint-test")
                .objectId(11L)
                .actorId(7L)
                .build(), request()));
        for(String entry:List.of("cutover","inspection"))assertThrows(CollectionOperationException.class,()->service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry(entry)
                .objectId(11L)
                .actorId(7L)
                .build(), request()));
        verifyNoInteractions(dispatch,tasks);
    }
    @Test void sourceAuthorizationFailureClearsSecretAndDoesNotDispatch(){
        when(source.authorize(any(CollectionSourceAdapter.AuthorizationQuery.class))).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        var r=request();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), r));
        assertArrayEquals(new char[6],r.getPassword());verifyNoInteractions(dispatch,tasks);
    }
    @Test void movedProjectCannotReplayReadCancelOrConsumeHistory(){
        service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), request());var row=saved();when(requests.findRequest(1L,request().getRequestKey())).thenReturn(row);when(requests.selectById(41L)).thenReturn(row);clearInvocations(dispatch);
        context=CollectionSourceAdapter.Source.builder()
                .entry("configuration")
                .objectId(11L)
                .sourceContext("IMP")
                .sourceObjectType("Configuration")
                .projectId(22L)
                .deviceId(31L)
                .deviceName("device")
                .version(0L)
                .manualAllowed(true)
                .canExecute(true)
                .completionMode("BUSINESS_CONSUMPTION")
                .title("配置调试")
                .build();
        assertThrows(IllegalStateException.class,()->service.submit(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), request()));
        assertThrows(IllegalStateException.class,()->service.findByRequestKey(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), request().getRequestKey()));
        assertThrows(IllegalStateException.class,()->service.cancel(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), 41L));
        assertThrows(IllegalStateException.class,()->service.consume(CollectionApplicationApi.SourceQuery.builder()
                .entry("configuration")
                .objectId(11L)
                .actorId(7L)
                .build(), 41L));verifyNoInteractions(dispatch,callbacks);
    }
    @Test
    void ownerPrecheckCreatesAnEmptyCommandTaskAndReplaysWithoutCreatingOrDispatchingAgain() {
        var profile = new CollectionExecutionProfile("PRECHECK", "");
        context = ownerProfile(profile);
        var request = request();
        request.setCommands(null);

        var result = service.submit(sourceQuery(), request);

        assertEquals("", result.commandText());
        assertEquals(profile, result.executionProfile());
        assertArrayEquals(new char[6], request.getPassword());
        var sent = ArgumentCaptor.forClass(CollectionDispatchApi.Command.class);
        verify(dispatch).dispatchManual(sent.capture());
        assertEquals("task", sent.getValue().platformTaskId());
        assertTrue(sent.getValue().commands().isEmpty());
        assertEquals(profile, sent.getValue().executionProfile());
        var row = saved();
        assertEquals("PRECHECK", row.getExecutionPurpose());
        assertEquals("", row.getCommandText());
        var ordered = inOrder(source, tasks, requests, tx, dispatch);
        ordered.verify(source).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        ordered.verify(tasks).createBatch(any());
        ordered.verify(requests).insert(any(CollectionRequestDO.class));
        ordered.verify(tx).commit(any());
        ordered.verify(dispatch).dispatchManual(any());

        when(requests.findRequest(1L, request.getRequestKey())).thenReturn(row);
        var replay = request();
        replay.setCommands(null);
        var replayed = service.submit(sourceQuery(), replay);

        assertEquals(result.id(), replayed.id());
        assertEquals(profile, replayed.executionProfile());
        assertEquals("task", replayed.task().platformTaskId());
        assertArrayEquals(new char[6], replay.getPassword());
        verify(tasks, times(1)).createBatch(any());
        verify(dispatch, times(1)).dispatchManual(any());
        verify(source, times(1)).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        verify(dispatch, never()).dispatchSaved(any());
        verifyNoInteractions(templates);
    }

    @Test
    void ownerInspectionCommandsUseTheResolvedSavedConnectionSnapshotAndReplayWithoutRedispatch() {
        var profile = new CollectionExecutionProfile("INSPECTION", "show version\nshow run");
        context = ownerProfile(profile);
        savedConnection();
        var request = request();
        request.setCredentialId(60L);
        request.setPassword(null);
        request.setCommands(null);
        request.setHost("client-only.example");
        request.setPort(4422);
        request.setUsername("client-only-user");

        var result = service.submit(sourceQuery(), request);

        assertEquals(profile.commandText(), result.commandText());
        assertEquals(profile, result.executionProfile());
        var authorized = ArgumentCaptor.forClass(CollectionSourceAdapter.PreparedConnection.class);
        verify(source).authorizeConnection(authorized.capture());
        var actual = authorized.getValue();
        assertSame(context, actual.source());
        assertEquals("stored.example", actual.host());
        assertEquals(5555, actual.port());
        assertEquals("SSH", actual.protocol());
        assertEquals("saved-user", actual.username());
        assertEquals(60L, actual.credentialId());
        assertEquals(4L, actual.credentialVersion());
        assertEquals(70L, actual.grantId());
        assertEquals(1L, actual.authorization().tenantId());
        assertEquals(7L, actual.authorization().actorId());
        assertEquals(11L, actual.authorization().objectId());
        assertEquals(31L, actual.authorization().deviceId());
        assertEquals(CollectionSourceAdapter.Access.EXECUTE, actual.authorization().access());
        assertEquals(0L, actual.authorization().expectedVersion());
        var batch = ArgumentCaptor.forClass(CollectionBatchCreateCommand.class);
        verify(tasks).createBatch(batch.capture());
        var item = batch.getValue().tasks().getFirst();
        assertEquals("stored.example", item.host());
        assertEquals(5555, item.port());
        assertEquals("SAVED_CREDENTIAL", item.credentialMode());
        assertEquals(60L, item.credentialId());
        assertEquals(70L, item.grantSnapshotId());
        assertEquals(CollectionTemplateService.hash(profile.commandText()), item.templateHash());
        var sent = ArgumentCaptor.forClass(CollectionDispatchApi.SavedCommand.class);
        verify(dispatch).dispatchSaved(sent.capture());
        assertEquals(List.of("show version", "show run"), sent.getValue().commands());
        assertEquals(profile, sent.getValue().executionProfile());
        assertEquals("saved-user", sent.getValue().username());
        assertEquals("dac-ref", sent.getValue().connectionId());
        assertEquals(4L, sent.getValue().connectionVersion());
        var ordered = inOrder(connections, source, tasks, tx, dispatch);
        ordered.verify(connections).resolve(7L, 60L, 21L, 31L, "SSH", null);
        ordered.verify(source).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        ordered.verify(tasks).createBatch(any());
        ordered.verify(tx).commit(any());
        ordered.verify(dispatch).dispatchSaved(any());

        var row = saved();
        when(requests.findRequest(1L, request.getRequestKey())).thenReturn(row);
        var replay = request();
        replay.setCredentialId(60L);
        replay.setPassword(null);
        replay.setCommands(profile.commandText());
        replay.setHost("client-only.example");
        replay.setPort(4422);
        replay.setUsername("client-only-user");
        assertEquals(result.id(), service.submit(sourceQuery(), replay).id());
        verify(tasks, times(1)).createBatch(any());
        verify(dispatch, times(1)).dispatchSaved(any());
        verify(connections, times(1)).resolve(7L, 60L, 21L, 31L, "SSH", null);
        verify(source, times(1)).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        verify(dispatch, never()).dispatchManual(any());
        verifyNoInteractions(templates);
    }

    @Test
    void ownerProfilesRejectClientCommandsAndReplacementTemplatesBeforeCreatingTasks() {
        for (var profile : List.of(new CollectionExecutionProfile("PRECHECK", ""),
                new CollectionExecutionProfile("INSPECTION", "show run"))) {
            context = ownerProfile(profile);
            var replacementCommands = request();
            replacementCommands.setCommands("client replacement command");
            assertThrows(CollectionOperationException.class, () -> service.submit(sourceQuery(), replacementCommands));
            assertArrayEquals(new char[6], replacementCommands.getPassword());

            var replacementTemplate = request();
            replacementTemplate.setCommands(profile.commandText());
            replacementTemplate.setTemplateId(50L);
            assertThrows(CollectionOperationException.class, () -> service.submit(sourceQuery(), replacementTemplate));
            assertArrayEquals(new char[6], replacementTemplate.getPassword());
        }
        verify(requests, never()).insert(any(CollectionRequestDO.class));
        verify(source, never()).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        verifyNoInteractions(connections, templates, tasks, dispatch);
    }

    @Test
    void ownerSavedConnectionRejectionStopsBeforeBatchPersistenceAndDispatch() {
        context = ownerProfile(new CollectionExecutionProfile("INSPECTION", "show run"));
        savedConnection();
        doThrow(new org.springframework.security.access.AccessDeniedException("Owner拒绝连接快照"))
                .when(source).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        var request = request();
        request.setCredentialId(60L);
        request.setPassword(null);
        request.setCommands(null);
        request.setHost("client-only.example");

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.submit(sourceQuery(), request));

        var authorized = ArgumentCaptor.forClass(CollectionSourceAdapter.PreparedConnection.class);
        verify(source).authorizeConnection(authorized.capture());
        assertEquals("stored.example", authorized.getValue().host());
        assertEquals(4L, authorized.getValue().credentialVersion());
        assertEquals(70L, authorized.getValue().grantId());
        verify(tasks, never()).createBatch(any());
        verify(requests, never()).insert(any(CollectionRequestDO.class));
        verifyNoInteractions(dispatch);
    }

    @Test
    void ownerTemporaryConnectionRejectionClearsPasswordAndDoesNotPersistOrDispatch() {
        context = ownerProfile(new CollectionExecutionProfile("PRECHECK", ""));
        var request = request();
        request.setCommands(null);
        doAnswer(call -> {
            var actual = call.<CollectionSourceAdapter.PreparedConnection>getArgument(0);
            assertEquals("device.example", actual.host());
            assertEquals(22, actual.port());
            assertEquals("SSH", actual.protocol());
            assertEquals("operator", actual.username());
            assertNull(actual.credentialId());
            assertNull(actual.credentialVersion());
            assertNull(actual.grantId());
            assertArrayEquals("secret".toCharArray(), request.getPassword());
            throw new org.springframework.security.access.AccessDeniedException("Owner拒绝临时连接");
        }).when(source).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.submit(sourceQuery(), request));

        assertArrayEquals(new char[6], request.getPassword());
        verify(source).authorizeConnection(any(CollectionSourceAdapter.PreparedConnection.class));
        verify(tasks, never()).createBatch(any());
        verify(requests, never()).insert(any(CollectionRequestDO.class));
        verifyNoInteractions(connections, dispatch);
    }

    private CollectionSourceAdapter.Source ownerProfile(CollectionExecutionProfile profile) {
        return CollectionSourceAdapter.Source.builder().entry(context.entry()).objectId(context.objectId())
                .sourceContext(context.sourceContext()).sourceObjectType(context.sourceObjectType())
                .projectId(context.projectId()).deviceId(context.deviceId()).deviceName(context.deviceName())
                .version(context.version()).manualAllowed(false).canExecute(context.canExecute())
                .completionMode(context.completionMode()).title(context.title()).executionProfile(profile).build();
    }

    private CollectionApplicationApi.SourceQuery sourceQuery() {
        return CollectionApplicationApi.SourceQuery.builder().entry("configuration").objectId(11L).actorId(7L).build();
    }

    private void savedConnection() {
        var credential = new DeviceCredentialDO();
        credential.setId(60L);
        when(connections.resolve(7L, 60L, 21L, 31L, "SSH", null)).thenReturn(
                new CollectionConnectionService.Resolved(credential, 70L,
                        new DeviceOpsResourceApi.Connection("dac-ref", "stored.example", 5555, "SSH", "saved-user", 4)));
    }

    private CollectionRequestDO saved(){var c=ArgumentCaptor.forClass(CollectionRequestDO.class);verify(requests).insert(c.capture());return c.getValue();}
    private CollectionExecutionRequest request(){var r=new CollectionExecutionRequest();r.setRequestKey("01234567-0123-0123-0123-012345678901");r.setExpectedVersion(0L);r.setHost("device.example");r.setPort(22);r.setProtocol("SSH");r.setUsername("operator");r.setPassword("secret".toCharArray());r.setCommands("show version");return r;}
    private CollectionTaskDTO task(String status){return new CollectionTaskDTO(1L,10L,"task","IMP","Configuration","11","21","31","device","device.example",22,"SSH","manual-11","v1","hash","TEMPORARY_SECRET",null,null,"key","BUSINESS_CONSUMPTION",status,"ACCEPTED",1L,51L,"IMP","Configuration","11",null,null);}
}
