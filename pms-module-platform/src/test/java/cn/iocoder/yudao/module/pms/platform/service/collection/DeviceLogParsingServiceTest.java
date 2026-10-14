package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceLogParsingApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionOperationException;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionSourceAdapter;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.DeviceLogParseDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.file.FileVersionMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceLogParsingServiceTest {
    DeviceLogParseMapper parses = mock(DeviceLogParseMapper.class);
    CollectionRequestMapper requests = mock(CollectionRequestMapper.class);
    CollectionTaskMapper tasks = mock(CollectionTaskMapper.class);
    FileVersionMapper files = mock(FileVersionMapper.class);
    CollectionAuthorization auth = mock(CollectionAuthorization.class);
    CollectionSourceAdapter source = mock(CollectionSourceAdapter.class);
    DeviceLogParsingApi remote = mock(DeviceLogParsingApi.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    DeviceLogParsingService service;
    DeviceLogParseDO saved;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(remote);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DeviceLogParsingService(parses, requests, tasks, files, auth, List.of(source), provider, mock(ObjectProvider.class), tx);
        when(parses.insert(any(DeviceLogParseDO.class))).thenAnswer(call -> {
            saved = call.getArgument(0); saved.setId(123L); return 1;
        });
        when(parses.selectByTenantAndIdForUpdate(1L, 123L)).thenAnswer(call -> saved);
        when(parses.selectById(123L)).thenAnswer(call -> saved);
        when(remote.submit(any())).thenReturn(result("QUEUED"));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void commitsIdentityBeforeIoAndReplayDoesNotSubmitAgain() {
        service.submit(7L, input("show version\nversion 1"));
        var order = inOrder(parses, tx, remote);
        order.verify(parses).insert(any(DeviceLogParseDO.class));
        order.verify(tx).commit(any()); order.verify(remote).submit(any());
        when(parses.selectByRequestKeyForUpdate(1L, input("").requestKey())).thenReturn(saved);
        when(remote.find("log-123", "21", null)).thenReturn(result("QUEUED"));
        service.submit(7L, input("show version\nversion 1"));
        verify(remote, times(1)).submit(any());
        assertThrows(CollectionOperationException.class, () -> service.submit(7L, input("changed")));
    }
    @Test void permissionDeniedDoesNotPersistOrSubmit() {
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(auth).permission(7L, "pms:device-collection:execute");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submit(7L, input("log")));
        verifyNoInteractions(parses, remote);
    }
    @Test void uncertainSubmissionCanBeQueriedWithoutResubmitting() {
        when(remote.submit(any())).thenThrow(new DeviceLogParsingApi.ParsingException("unavailable"));
        assertEquals("SUBMITTING", service.submit(7L, input("log")).state());
        service.get(7L, 123L);
        verify(remote, times(1)).submit(any());
        verify(remote).find("log-123", "21", null);
    }
    @Test void changedRemoteIdentityAndTenantAreRejected() {
        service.submit(7L, input("log"));
        when(remote.find("log-123", "21", null)).thenReturn(new DeviceLogParsingApi.ParseTask("foreign", "QUEUED", null, "1.4.0", List.of(), null));
        assertNull(service.get(7L, 123L).result());
        assertTrue(service.get(7L, 123L).message().contains("标识发生变化"));
        saved.setTenantId(2L);
        assertThrows(CollectionOperationException.class, () -> service.get(7L, 123L));
    }
    @Test void collectedInputCannotReplaceEvidenceOrReadUnregisteredSource() {
        var invalid = new DeviceLogParsingService.Input(input("").requestKey(), 21L, null, "replacement", null, "center", 21L, 41L);
        assertThrows(CollectionOperationException.class, () -> service.submit(7L, invalid));
        var unknown = new DeviceLogParsingService.Input(input("").requestKey(), 21L, null, null, null, "unknown", 21L, 41L);
        assertThrows(CollectionOperationException.class, () -> service.submit(7L, unknown));
        verifyNoInteractions(remote, parses);
    }
    @Test void collectedSourceUsesFrozenFileAndNativeCollectionWithoutWritingBusinessRecords() {
        collectedSource();
        var result = service.submit(7L, collectedInput());
        assertEquals(51L, result.sourceFileVersionId());
        verify(remote).submit(new DeviceLogParsingApi.Submission("log-123", "21", "collection-1", null, null));
        verify(auth, atLeastOnce()).permission(7L, "pms:file:download");
        verify(tasks, never()).updateById(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO.class));
        verify(requests, never()).updateById(any(cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionRequestDO.class));
    }
    @Test void quarantinedFileOrMovedBusinessSourceCannotReadPreviousResult() {
        var file = collectedSource(); service.submit(7L, collectedInput()); clearInvocations(remote);
        file.setAvailabilityStatusCode("QUARANTINED");
        assertThrows(CollectionOperationException.class, () -> service.get(7L, 123L));
        file.setAvailabilityStatusCode("AVAILABLE");
        when(source.authorize(any(CollectionSourceAdapter.AuthorizationQuery.class))).thenReturn(
                CollectionSourceAdapter.Source.builder()
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
                        .build());
        assertThrows(CollectionOperationException.class, () -> service.get(7L, 123L));
        verifyNoInteractions(remote);
    }
    @Test void wrongEntityAndProjectAreRejectedBeforePersistence() {
        collectedSource();
        var wrong = new DeviceLogParsingService.Input(input("").requestKey(), 22L, null, null, null, "configuration", 11L, 41L);
        assertThrows(CollectionOperationException.class, () -> service.submit(7L, wrong));
        var foreign = new DeviceLogParsingService.Input(input("").requestKey(), 21L, null, null, null, "configuration", 12L, 41L);
        assertThrows(CollectionOperationException.class, () -> service.submit(7L, foreign));
        verifyNoInteractions(parses, remote);
    }
    private cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO collectedSource() {
        when(source.entry()).thenReturn("configuration");
        when(source.authorize(any(CollectionSourceAdapter.AuthorizationQuery.class))).thenReturn(
                CollectionSourceAdapter.Source.builder()
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
                        .build());
        var request = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionRequestDO();
        request.setId(41L); request.setTenantId(1L); request.setEntry("configuration"); request.setObjectId(11L); request.setPlatformTaskId("platform-1");
        when(requests.selectById(41L)).thenReturn(request);
        var task = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO();
        task.setTenantId(1L); task.setProjectId("21"); task.setSourceContext("IMP"); task.setSourceObjectType("Configuration");
        task.setSourceObjectId("11"); task.setDeviceId("31"); task.setPlatformTaskId("platform-1"); task.setExternalTaskId("collection-1"); task.setFileVersionId(51L);
        when(tasks.selectByTenantAndPlatformTaskId(1L, "platform-1")).thenReturn(task);
        var file = new cn.iocoder.yudao.module.pms.platform.dal.dataobject.file.FileVersionDO();
        file.setId(51L); file.setTenantId(1L); file.setAvailabilityStatusCode("AVAILABLE");
        when(files.selectByTenantAndId(1L, 51L)).thenReturn(file);
        return file;
    }
    private DeviceLogParsingService.Input collectedInput() {
        return new DeviceLogParsingService.Input(input("").requestKey(), 21L, null, null, null, "configuration", 11L, 41L);
    }
    private DeviceLogParsingService.Input input(String text) {
        return new DeviceLogParsingService.Input("01234567-0123-0123-0123-012345678901", 21L, "device.log", text, null, null, null, null);
    }
    private DeviceLogParsingApi.ParseTask result(String state) {
        return new DeviceLogParsingApi.ParseTask("parse-1", state, null, "1.4.0", List.of(), null);
    }
}
