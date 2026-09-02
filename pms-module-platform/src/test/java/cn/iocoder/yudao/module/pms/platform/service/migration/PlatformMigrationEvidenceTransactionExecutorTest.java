package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.query.MigrationBatchClassificationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException.Code.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlatformMigrationEvidenceTransactionExecutorTest {

    private static final String SHA256 = "a".repeat(64);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 20, 0);

    @Mock private MigrationBatchMapper batchMapper;
    @Mock private MigrationSourceRecordMapper sourceMapper;
    @Mock private ExternalKeyMappingMapper mappingMapper;
    @Mock private MigrationIssueMapper issueMapper;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;

    private PlatformMigrationEvidenceTransactionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PlatformMigrationEvidenceTransactionExecutor(
                batchMapper, sourceMapper, mappingMapper, issueMapper, commandExecutionApi);
        answerNewCommands();
    }

    @Test
    void createsImportBatchWithSystemAuditFieldsAndStableSuccessFacts() {
        when(batchMapper.insert(any(MigrationBatchDO.class))).thenAnswer(invocation -> {
            MigrationBatchDO row = invocation.getArgument(0);
            row.setId(101L);
            return 1;
        });
        CreateImportBatchCommand command = new CreateImportBatchCommand(
                7L, "COM", "F-COM-001", "R1", "ERP", "orders",
                "schema-v1", 1, SHA256, NOW, null, null, "key-1", "corr-1");

        MigrationBatchFact result = executor.createImportBatch(command);

        assertEquals(101L, result.batchId());
        assertEquals(MigrationBatchStatus.IMPORTING, result.status());
        MigrationBatchDO inserted = captureInsertedBatch();
        assertEquals("0", inserted.getCreator());
        assertEquals("0", inserted.getUpdater());
        assertNotNull(inserted.getCreateTime());
        assertNotNull(inserted.getUpdateTime());
    }

    @Test
    void replaysMappedTargetsByContractOrderRatherThanDatabaseIdOrder() {
        MigrationBatchDO batch = batch(22L, MigrationBatchStatus.RECONCILING, 2);
        when(batchMapper.selectByTenantAndIdForUpdate(any())).thenReturn(batch);
        when(sourceMapper.selectByBatchAndIdForUpdate(any())).thenReturn(source(31L, 22L));
        when(issueMapper.selectListBySource(any())).thenReturn(List.of());
        ExternalKeyMappingDO second = mapping(91L, 31L, "PRIMARY", 1, 402L);
        ExternalKeyMappingDO first = mapping(92L, 31L, "PRIMARY", 0, 401L);
        when(mappingMapper.selectListBySource(any())).thenReturn(List.of(second, first));
        AppendExternalMappingCommand command = new AppendExternalMappingCommand(
                7L, 22L, 31L, SourceReconciliationType.MAPPED,
                List.of(target(402L, 1), target(401L, 0)), "map-key", "corr");

        SourceReconciliationResult result = executor.appendExternalMapping(command);

        assertEquals(List.of(91L, 92L), result.mappingIds());
        verify(mappingMapper, never()).insert(any(ExternalKeyMappingDO.class));
    }

    @Test
    void claimReturnsAuthoritativeVersionAndCompletionUsesSingleCas() {
        MigrationBatchDO staged = batch(22L, MigrationBatchStatus.STAGED_READY, 1);
        staged.setSourceCount(2L);
        when(batchMapper.selectNextStagedForUpdate(any())).thenReturn(staged);
        when(batchMapper.claim(any(), eq(1))).thenReturn(1);

        MigrationBatchClaimResult claim = executor.claimStagedBatch(new ClaimStagedBatchCommand(
                7L, "COM", "F-COM-001", List.of("ERP"), List.of("orders"), "corr"));

        assertTrue(claim.claimed());
        assertEquals(2, claim.batch().version());
        when(batchMapper.selectByTenantAndIdForUpdate(any())).thenReturn(staged);
        when(sourceMapper.selectClassificationSummary(any())).thenReturn(
                new MigrationBatchClassificationSummary(2, 1, 1, 0, 0, 0));
        when(batchMapper.complete(any())).thenReturn(1);
        MigrationBatchFact completed = executor.completeReconciliation(new CompleteReconciliationCommand(
                7L, 22L, 2, 2, 1, 1, 0, "rules-v1", "complete-key", "corr"));

        assertEquals(MigrationBatchStatus.COMPLETED, completed.status());
        assertEquals(3, completed.version());
        verify(batchMapper).complete(argThat(update -> update.expectedVersion() == 2));
    }

    @Test
    void conflictingCreateIdentityIsBusinessStateConflict() {
        when(batchMapper.insert(any(MigrationBatchDO.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        CreateImportBatchCommand command = new CreateImportBatchCommand(
                7L, "COM", "F-COM-001", "R1", "ERP", "orders",
                "schema-v1", 1, SHA256, NOW, null, null, "key-1", "corr-1");

        PlatformMigrationEvidenceException error = assertThrows(
                PlatformMigrationEvidenceException.class, () -> executor.createImportBatch(command));

        assertEquals(BATCH_STATE_CONFLICT, error.getCode());
    }

    @Test
    void retainedPersistsClassificationWithoutExposingInternalMappingId() {
        when(batchMapper.selectByTenantAndIdForUpdate(any()))
                .thenReturn(batch(22L, MigrationBatchStatus.RECONCILING, 2));
        when(sourceMapper.selectByBatchAndIdForUpdate(any())).thenReturn(source(31L, 22L));
        when(issueMapper.selectListBySource(any())).thenReturn(List.of());
        when(mappingMapper.selectListBySource(any())).thenReturn(List.of());
        when(mappingMapper.insert(any(ExternalKeyMappingDO.class))).thenAnswer(invocation -> {
            ExternalKeyMappingDO row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        });

        SourceReconciliationResult result = executor.appendExternalMapping(
                new AppendExternalMappingCommand(7L, 22L, 31L,
                        SourceReconciliationType.RETAINED, List.of(), "retain-key", "corr"));

        assertTrue(result.mappingIds().isEmpty());
        verify(mappingMapper).insert(argThat((ExternalKeyMappingDO row) -> row.getResultType().equals("RETAINED")
                && row.getCreator().equals("0")));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void answerNewCommands() {
        when(commandExecutionApi.execute(any(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier operation = invocation.getArgument(3);
            Function factsFactory = invocation.getArgument(4);
            Object response = operation.get();
            PlatformCommandExecutionApi.SuccessFacts facts =
                    (PlatformCommandExecutionApi.SuccessFacts) factsFactory.apply(response);
            assertNotNull(facts.correlationId());
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    private MigrationBatchDO captureInsertedBatch() {
        org.mockito.ArgumentCaptor<MigrationBatchDO> captor =
                org.mockito.ArgumentCaptor.forClass(MigrationBatchDO.class);
        verify(batchMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static MigrationBatchDO batch(Long id, MigrationBatchStatus status, int version) {
        MigrationBatchDO row = new MigrationBatchDO();
        row.setId(id);
        row.setTenantId(7L);
        row.setOwnerContextCode("COM");
        row.setPurposeCode("F-COM-001");
        row.setReleaseId("R1");
        row.setSourceSystem("ERP");
        row.setSourceTable("orders");
        row.setBatchStatus(status.name());
        row.setSourceCount(0L);
        row.setMappedCount(0L);
        row.setIssueCount(0L);
        row.setRetainedCount(0L);
        row.setVersion(version);
        row.setCreateTime(NOW);
        return row;
    }

    private static MigrationSourceRecordDO source(Long id, Long batchId) {
        MigrationSourceRecordDO row = new MigrationSourceRecordDO();
        row.setId(id);
        row.setTenantId(7L);
        row.setBatchId(batchId);
        return row;
    }

    private static ExternalKeyMappingDO mapping(Long id, Long sourceId, String role, int sequence, Long targetId) {
        ExternalKeyMappingDO row = new ExternalKeyMappingDO();
        row.setId(id);
        row.setTenantId(7L);
        row.setBatchId(22L);
        row.setSourceRecordId(sourceId);
        row.setResultType(SourceReconciliationType.MAPPED.name());
        row.setTargetContext("COM");
        row.setTargetObjectType("ORDER");
        row.setTargetTable("com_sales_order");
        row.setTargetId(targetId);
        row.setTargetRole(role);
        row.setTargetSequence(sequence);
        return row;
    }

    private static ExternalTargetMapping target(Long id, int sequence) {
        return new ExternalTargetMapping("COM", "ORDER", "com_sales_order", id, "PRIMARY", sequence);
    }
}
