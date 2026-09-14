package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationBatchDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationBatchClassificationSummary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Regression from a real 865-row integration preview; no database or source access. */
class PlatformMigrationStagingCountTest {
    private final MigrationBatchMapper batches = mock(MigrationBatchMapper.class);
    private final MigrationSourceRecordMapper sources = mock(MigrationSourceRecordMapper.class);
    private final PlatformCommandExecutionApi commands = mock(PlatformCommandExecutionApi.class);
    private final PlatformMigrationEvidenceTransactionExecutor executor = new PlatformMigrationEvidenceTransactionExecutor(
            batches, sources, mock(ExternalKeyMappingMapper.class), mock(MigrationIssueMapper.class), commands);

    @ParameterizedTest @ValueSource(longs = {127,128,865,10000})
    void stagesEqualCountsRegardlessOfBoxingCache(long count) {
        prepare(count, count);
        when(batches.transition(any())).thenReturn(1);
        var result = executor.markStagedReady(command(count));
        assertEquals(MigrationBatchStatus.STAGED_READY, result.status());
        assertEquals(count, result.sourceCount());
    }

    @Test void stillRejectsAnIncompleteSnapshot() {
        prepare(865, 864);
        assertEquals(PlatformMigrationEvidenceException.Code.COUNT_MISMATCH,
                assertThrows(PlatformMigrationEvidenceException.class,
                        () -> executor.markStagedReady(command(865))).getCode());
        verify(batches, never()).transition(any());
    }

    private MarkStagedReadyCommand command(long count) {
        return new MarkStagedReadyCommand(1L,1L,0,ImportStagingDecision.READY,
                Long.valueOf(count),"SYNC_V1","a".repeat(64),null,"ready:1","1");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void prepare(long expected, long actual) {
        var batch = new MigrationBatchDO();
        batch.setId(1L); batch.setTenantId(1L); batch.setOwnerContextCode("INT");
        batch.setPurposeCode("SYNC_TEST"); batch.setReleaseId("test");
        batch.setSourceSystem("TEST"); batch.setSourceTable("fixture");
        batch.setManifestSchemaVersion("SYNC_V1"); batch.setContentSha256("a".repeat(64));
        batch.setExpectedRowCount(Long.valueOf(expected)); batch.setBatchStatus("IMPORTING");
        batch.setVersion(0); batch.setSourceCount(0L); batch.setMappedCount(0L);
        batch.setIssueCount(0L); batch.setRetainedCount(0L); batch.setCreateTime(LocalDateTime.now());
        when(batches.selectByTenantAndIdForUpdate(any())).thenReturn(batch);
        when(sources.selectClassificationSummary(any())).thenReturn(
                new MigrationBatchClassificationSummary(actual,0,0,0,actual,0));
        when(commands.execute(any(),anyString(),any(),any(),any())).thenAnswer(invocation ->
                new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,
                        ((Supplier) invocation.getArgument(3)).get()));
    }
}
