package cn.iocoder.yudao.module.pms.platform.export;

import cn.iocoder.yudao.module.pms.platform.api.export.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.service.export.ExportBusinessDataProviderRegistry;
import cn.iocoder.yudao.module.pms.platform.service.export.ExportTaskApiImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportTaskApiImplTest {

    private final PlatformExportTaskMapper taskMapper = mock(PlatformExportTaskMapper.class);
    private final PlatformExportAuditMapper auditMapper = mock(PlatformExportAuditMapper.class);
    private final ExportBusinessDataProvider provider = mock(ExportBusinessDataProvider.class);
    private final ExportTaskApiImpl service = new ExportTaskApiImpl();

    @BeforeEach
    void setUp() {
        when(provider.ownerContext()).thenReturn("ACC");
        when(provider.exportType()).thenReturn("SATISFACTION_RESULT");
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "auditMapper", auditMapper);
        ReflectionTestUtils.setField(service, "providerRegistry",
                new ExportBusinessDataProviderRegistry(List.of(provider)));
        when(auditMapper.selectNextSequenceForUpdate(anyLong(), anyLong())).thenReturn(1);
        when(auditMapper.insert(any(PlatformExportAuditDO.class))).thenReturn(1);
    }

    @Test
    void requestFreezesProviderSnapshotAndReplaysSameOperation() {
        when(provider.inspect(any())).thenReturn(available(7L));
        when(taskMapper.insertIfAbsent(any())).thenReturn(1);

        ExportTaskFact created = service.request(request("operation-1"));
        assertEquals("REQUESTED", created.status());
        ArgumentCaptor<PlatformExportTaskDO> captor = ArgumentCaptor.forClass(PlatformExportTaskDO.class);
        verify(taskMapper).insertIfAbsent(captor.capture());
        assertEquals(7L, captor.getValue().getScopeVersion());
        assertEquals("[\"projectCode\",\"score\"]", captor.getValue().getRequestedFieldsSnapshot());
        verify(auditMapper).insert(argThat((PlatformExportAuditDO row) -> "REQUESTED".equals(row.getActionCode())));

        PlatformExportTaskDO existing = captor.getValue();
        when(taskMapper.selectByIdentity(any())).thenReturn(existing);
        assertEquals(existing.getId(), service.request(request("operation-1")).taskId());
        verify(taskMapper, times(1)).insertIfAbsent(any());
    }

    @Test
    void sameOperationWithDifferentPayloadConflicts() {
        when(provider.inspect(any())).thenReturn(available(7L));
        PlatformExportTaskDO existing = existingFailed();
        existing.setRequestDigest("0".repeat(64));
        when(taskMapper.selectByIdentity(any())).thenReturn(existing);
        assertThrows(IllegalStateException.class, () -> service.request(request("operation-1")));
        verify(taskMapper, never()).insertIfAbsent(any());
    }

    @Test
    void retryRequiresOriginalActorRetryableFailureAndExactVersion() {
        PlatformExportTaskDO existing = existingFailed();
        when(taskMapper.selectByActorForUpdate(any())).thenReturn(existing);
        when(provider.inspect(any())).thenReturn(available(7L));
        when(taskMapper.retryFailed(any())).thenReturn(1);

        ExportTaskFact result = service.retry(new ExportTaskRetryCommand(0L, 9L, 31L, 4));
        assertEquals("REQUESTED", result.status());
        assertEquals(3, result.retryCount());
        assertEquals(5, result.version());
        verify(taskMapper).retryFailed(argThat(update -> update.expectedVersion() == 4));
        verify(auditMapper).insert(argThat((PlatformExportAuditDO row) -> "RETRY_REQUESTED".equals(row.getActionCode())));
    }

    @Test
    void retryRejectsScopeVersionDriftWithoutWrite() {
        when(taskMapper.selectByActorForUpdate(any())).thenReturn(existingFailed());
        when(provider.inspect(any())).thenReturn(available(8L));
        assertThrows(IllegalStateException.class,
                () -> service.retry(new ExportTaskRetryCommand(0L, 9L, 31L, 4)));
        verify(taskMapper, never()).retryFailed(any());
        verify(auditMapper, never()).insert(any(PlatformExportAuditDO.class));
    }

    private ExportTaskRequestCommand request(String operationId) {
        return new ExportTaskRequestCommand(0L, 9L, operationId, "ACC", "SATISFACTION_RESULT",
                "{\"projectIds\":[1]}", List.of("score", "projectCode"), false);
    }

    private ExportBusinessDataSnapshot available(Long scopeVersion) {
        return new ExportBusinessDataSnapshot("AVAILABLE", "{\"projectIds\":[1]}", "{\"rootId\":1}",
                List.of("projectCode", "score"), false, scopeVersion, List.of());
    }

    private PlatformExportTaskDO existingFailed() {
        PlatformExportTaskDO row = new PlatformExportTaskDO();
        row.setId(31L);
        row.setTenantId(0L);
        row.setOwnerContext("ACC");
        row.setExportType("SATISFACTION_RESULT");
        row.setOperationId("operation-1");
        row.setActorUserId(9L);
        row.setFilterSnapshot("{\"projectIds\":[1]}");
        row.setScopeSnapshot("{\"rootId\":1}");
        row.setRequestedFieldsSnapshot("[\"projectCode\",\"score\"]");
        row.setIncludeFiles(false);
        row.setScopeVersion(7L);
        row.setTaskStatus("FAILED");
        row.setFailureCode("PROVIDER_TEMPORARILY_UNAVAILABLE");
        row.setFailureRetryable(true);
        row.setRetryCount(2);
        row.setVersion(4);
        return row;
    }
}
