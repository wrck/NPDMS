package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionDesktopSourceServiceTest {
    private final CollectionRequestMapper requests = mock(CollectionRequestMapper.class);
    private final CollectionTaskMapper rows = mock(CollectionTaskMapper.class);
    private final CollectionTaskApi tasks = mock(CollectionTaskApi.class);
    private final CollectionSourceAdapter adapter = mock(CollectionSourceAdapter.class);
    private final CollectionAuthorization permissions = mock(CollectionAuthorization.class);
    private final CollectionDesktopSourceApi.TaskKey key = new CollectionDesktopSourceApi.TaskKey(1L, "task-1");
    private CollectionDesktopSourceService service;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        service = new CollectionDesktopSourceService(requests, rows, tasks, List.of(adapter), permissions);
        var request = new CollectionRequestDO();
        request.setDispatchMode("DESKTOP"); request.setActorId(42L); request.setEntry("configuration");
        request.setObjectId(5L); request.setDesktopSourceVersion(3L); request.setCommandText("show version");
        when(requests.findByTask(1L, "task-1")).thenReturn(request);
        var task = mock(CollectionTaskDTO.class);
        when(task.credentialMode()).thenReturn("LOCAL_TEMPORARY"); when(task.projectId()).thenReturn("7"); when(task.deviceId()).thenReturn("8");
        when(task.sourceContext()).thenReturn("IMP"); when(task.sourceObjectType()).thenReturn("Configuration");
        when(tasks.getTask(1L, "task-1")).thenReturn(task);
        when(rows.selectByTenantAndPlatformTaskId(1L, "task-1")).thenReturn(new CollectionTaskDO());
        when(adapter.entry()).thenReturn("configuration");
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void revalidatesOriginalActorSourceVersionAndConnectionBeforeReleasingCommands() {
        when(adapter.authorize(any())).thenAnswer(invocation -> {
            CollectionSourceAdapter.AuthorizationQuery query = invocation.getArgument(0);
            assertEquals(42L, query.actorId()); assertEquals(3L, query.expectedVersion());
            assertEquals(CollectionSourceAdapter.Access.EXECUTE, query.access());
            return CollectionSourceAdapter.Source.builder().projectId(7L).deviceId(8L).sourceContext("IMP")
                    .sourceObjectType("Configuration").canExecute(true).build();
        });
        assertEquals("show version", service.snapshot(key).commandText());
        verify(adapter).authorizeConnection(any());
    }

    @Test void revokedOrChangedSourceCannotObtainAnExecutionSnapshot() {
        when(adapter.authorize(any())).thenThrow(new CollectionOperationException("来源版本已变化"));
        assertThrows(CollectionOperationException.class, () -> service.snapshot(key));
        verify(adapter, never()).authorizeConnection(any());
    }

    @Test void repeatedAcceptanceCannotRewriteTerminalHistoryOrChangeCollectionBinding() {
        var row = new CollectionTaskDO(); row.setCredentialMode("LOCAL_TEMPORARY"); row.setStatus("COMPLETED"); row.setExternalTaskId("collection-1");
        when(rows.selectByTenantAndPlatformTaskIdForUpdate(1L, "task-1")).thenReturn(row);
        assertDoesNotThrow(() -> service.accepted(new CollectionDesktopSourceApi.Acceptance(key, "collection-1", "SUCCEEDED")));
        assertThrows(CollectionOperationException.class, () -> service.accepted(new CollectionDesktopSourceApi.Acceptance(key, "collection-2", "SUCCEEDED")));
        verify(rows, never()).updateDispatchState(any());
    }
}
