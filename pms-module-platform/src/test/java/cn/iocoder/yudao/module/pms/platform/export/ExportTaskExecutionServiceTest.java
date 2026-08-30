package cn.iocoder.yudao.module.pms.platform.export;

import cn.iocoder.yudao.module.pms.platform.api.export.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.export.PlatformExportTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.export.PlatformExportTaskMapper;
import cn.iocoder.yudao.module.pms.platform.service.export.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExportTaskExecutionServiceTest {

    private final PlatformExportTaskMapper taskMapper = mock(PlatformExportTaskMapper.class);
    private final PlatformExportAuditMapper auditMapper = mock(PlatformExportAuditMapper.class);
    private final ExportBusinessDataProvider provider = mock(ExportBusinessDataProvider.class);
    private final ExportFileWriter fileWriter = mock(ExportFileWriter.class);
    private ExportTaskExecutionService service;

    @BeforeEach
    void setUp() {
        when(provider.ownerContext()).thenReturn("ACC");
        when(provider.exportType()).thenReturn("SATISFACTION_RESULT");
        service = new ExportTaskExecutionService(taskMapper, auditMapper,
                new ExportBusinessDataProviderRegistry(List.of(provider)), fileWriter);
        when(auditMapper.selectNextSequenceForUpdate(anyLong(), anyLong())).thenReturn(1, 2, 3);
        when(auditMapper.insert(any(PlatformExportAuditDO.class))).thenReturn(1);
        when(taskMapper.transition(any())).thenReturn(1);
    }

    @Test
    void successCreatesCsvAndPublishesFileFact() {
        when(taskMapper.selectRequestedForUpdate(any())).thenReturn(List.of(requested()));
        when(provider.generate(any())).thenReturn(new ExportBusinessDataSnapshot("AVAILABLE", "{}", "{}",
                List.of("name", "score"), false, 7L, List.of(List.of("A", "95"))));
        when(fileWriter.write(any())).thenReturn(new ExportFileWriter.WrittenExportFile(
                51L, 1, "export-task-31", 1, 1, 0, "a".repeat(64)));

        assertEquals(1, service.executeRequested(0L));
        verify(fileWriter).write(argThat(command -> new String(command.content()).contains("\"A\",\"95\"")));
        verify(taskMapper, times(2)).transition(any());
        verify(auditMapper).insert(argThat((PlatformExportAuditDO row) -> "SUCCEEDED".equals(row.getActionCode())));
    }

    @Test
    void temporaryProviderFailureIsRetryableFailed() {
        when(taskMapper.selectRequestedForUpdate(any())).thenReturn(List.of(requested()));
        when(provider.generate(any())).thenReturn(new ExportBusinessDataSnapshot(
                "TEMPORARILY_UNAVAILABLE", "{}", "{}", List.of("name"), false, 7L, List.of()));
        service.executeRequested(0L);
        verify(taskMapper).transition(argThat(update -> "FAILED".equals(update.targetStatus())
                && Boolean.TRUE.equals(update.failureRetryable())));
        verify(fileWriter, never()).write(any());
    }

    @Test
    void scopeRejectionIsRejectedNotRetryable() {
        when(taskMapper.selectRequestedForUpdate(any())).thenReturn(List.of(requested()));
        when(provider.generate(any())).thenReturn(new ExportBusinessDataSnapshot(
                "REJECTED", "{}", "{}", List.of("name"), false, 7L, List.of()));
        service.executeRequested(0L);
        verify(taskMapper).transition(argThat(update -> "REJECTED".equals(update.targetStatus())
                && update.failureRetryable() == null));
        verify(fileWriter, never()).write(any());
    }

    @Test
    void missingProviderIsPermanentContractFailure() {
        service = new ExportTaskExecutionService(taskMapper, auditMapper,
                new ExportBusinessDataProviderRegistry(List.of()), fileWriter);
        when(taskMapper.selectRequestedForUpdate(any())).thenReturn(List.of(requested()));

        service.executeRequested(0L);

        verify(taskMapper).transition(argThat(update -> "FAILED".equals(update.targetStatus())
                && Boolean.FALSE.equals(update.failureRetryable())
                && "PROVIDER_CONTRACT_INVALID".equals(update.failureCode())));
        verify(fileWriter, never()).write(any());
    }

    private PlatformExportTaskDO requested() {
        PlatformExportTaskDO row = new PlatformExportTaskDO();
        row.setId(31L); row.setTenantId(0L); row.setOwnerContext("ACC");
        row.setExportType("SATISFACTION_RESULT"); row.setOperationId("op"); row.setActorUserId(9L);
        row.setFilterSnapshot("{}"); row.setRequestedFieldsSnapshot("[\"name\",\"score\"]");
        row.setIncludeFiles(false); row.setScopeVersion(7L); row.setTaskStatus("REQUESTED"); row.setVersion(0);
        return row;
    }
}
