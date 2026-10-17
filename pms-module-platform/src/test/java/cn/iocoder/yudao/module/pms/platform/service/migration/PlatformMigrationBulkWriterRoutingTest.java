package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformMigrationBulkWriterRoutingTest {
    private final MigrationBatchMapper batches=mock(MigrationBatchMapper.class);
    private final MigrationSourceRecordMapper sources=mock(MigrationSourceRecordMapper.class);
    private final ExternalKeyMappingMapper mappings=mock(ExternalKeyMappingMapper.class);
    private final MigrationIssueMapper issues=mock(MigrationIssueMapper.class);
    private final PlatformCommandExecutionApi commands=mock(PlatformCommandExecutionApi.class);
    private final MigrationEvidenceJdbcWriter writer=mock(MigrationEvidenceJdbcWriter.class);
    private final PlatformMigrationEvidenceTransactionExecutor executor=new PlatformMigrationEvidenceTransactionExecutor(
            batches,sources,mappings,issues,commands,writer);

    @Test void sourceAppendUsesBulkWriterAfterAssigningIdentityAndAudit() {
        batch(MigrationBatchStatus.IMPORTING);
        when(sources.selectByKeys(any())).thenReturn(List.of());
        var result=executor.appendSourceRecords(new AppendMigrationSourceRecordsCommand(List.of(
                new AppendMigrationSourceRecordCommand(7L,31L,"ERP","orders","one",null,"{}","a".repeat(64),LocalDateTime.of(2026,10,10,8,0),"correlation"))));
        assertEquals(1,result.size());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<MigrationSourceRecordDO>> captor=ArgumentCaptor.forClass(List.class);
        verify(writer).insertSources(captor.capture());
        var row=captor.getValue().getFirst();assertTrue(row.getId()>0);assertEquals(7L,row.getTenantId());assertEquals(31L,row.getBatchId());
        assertEquals("0",row.getCreator());assertNotNull(row.getCreateTime());assertEquals("{}",row.getSourcePayload());
        verify(sources,never()).insertRows(anyList());
    }
    @Test void mappingAppendUsesBulkWriterAfterVisibilityAndClassificationChecks() {
        batch(MigrationBatchStatus.RECONCILING);answerCommand();
        var source=new MigrationSourceRecordDO();source.setId(51L);
        when(sources.selectSourcePage(any())).thenReturn(List.of(source));
        when(issues.selectSourcePage(any())).thenReturn(List.of());when(mappings.selectSourcePage(any())).thenReturn(List.of());
        executor.appendExternalMappings(command());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<ExternalKeyMappingDO>> captor=ArgumentCaptor.forClass(List.class);
        verify(writer).insertMappings(captor.capture());
        var row=captor.getValue().getFirst();assertTrue(row.getId()>0);assertEquals(7L,row.getTenantId());assertEquals(31L,row.getBatchId());
        assertEquals(51L,row.getSourceRecordId());assertEquals("RETAINED",row.getResultType());assertEquals("0",row.getCreator());assertNotNull(row.getCreateTime());
        verify(mappings,never()).insertRows(anyList());
    }
    @Test void mappingReplayStillDoesNotWrite() {
        batch(MigrationBatchStatus.RECONCILING);answerCommand();
        var source=new MigrationSourceRecordDO();source.setId(51L);
        var prior=new ExternalKeyMappingDO();prior.setSourceRecordId(51L);prior.setResultType("RETAINED");
        when(sources.selectSourcePage(any())).thenReturn(List.of(source));
        when(issues.selectSourcePage(any())).thenReturn(List.of());when(mappings.selectSourcePage(any())).thenReturn(List.of(prior));
        executor.appendExternalMappings(command());
        verifyNoInteractions(writer);verify(mappings,never()).insertRows(anyList());
    }
    private AppendExternalMappingsCommand command() {
        return new AppendExternalMappingsCommand(List.of(new AppendExternalMappingCommand(7L,31L,51L,SourceReconciliationType.RETAINED,List.of(),"row-key","correlation")),"page-key","correlation");
    }
    private void batch(MigrationBatchStatus status) {
        var batch=new MigrationBatchDO();batch.setId(31L);batch.setTenantId(7L);batch.setSourceSystem("ERP");batch.setSourceTable("orders");batch.setBatchStatus(status.name());
        when(batches.selectByTenantAndIdForUpdate(any())).thenReturn(batch);
    }
    private void answerCommand() {
        when(commands.execute(any(),anyString(),any(),any(),any())).thenAnswer(invocation->{
            Supplier<?> action=invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(PlatformCommandExecutionApi.Decision.NEW,action.get());
        });
    }
}
