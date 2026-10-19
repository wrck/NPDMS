package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncEvidenceServiceTest {
    record Identity(Long tenant, String owner, String purpose, String release, String system, String table) {}

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sharedPhysicalTableKeepsSeparateLogicalEvidenceAndStreamingChunks(boolean caseOnlyNames) {
        var api = mock(PlatformMigrationEvidenceApi.class);
        var batches = new LinkedHashMap<Identity, MigrationBatchFact>();
        var claimed = new HashSet<Long>();
        var sourceRecords = new ArrayList<MigrationSourceRecordFact>();
        var completed = new ArrayList<CompleteReconciliationCommand>();
        var idempotencyKeys = new HashSet<String>();
        when(api.createImportBatch(any())).thenAnswer(inv -> {
            CreateImportBatchCommand c = inv.getArgument(0);
            assertTrue(idempotencyKeys.add(c.idempotencyKey().toLowerCase(Locale.ROOT)), "case-insensitive idempotency collision");
            var key = new Identity(c.tenantId(), c.ownerContextCode(), c.purposeCode(), c.releaseId().toLowerCase(Locale.ROOT), c.sourceSystem(), c.sourceTable());
            if (batches.containsKey(key)) throw new IllegalStateException("duplicate PLT batch identity");
            var batch = new MigrationBatchFact((long)batches.size()+1, c.tenantId(), c.ownerContextCode(), c.purposeCode(),
                    c.releaseId(), c.sourceSystem(), c.sourceTable(), MigrationBatchStatus.IMPORTING, c.expectedRowCount(),
                    0, 0, 0, null, 0, c.exportedAt());
            batches.put(key, batch); return batch;
        });
        when(api.appendSourceRecords(any())).thenAnswer(inv -> {
            AppendMigrationSourceRecordsCommand page = inv.getArgument(0);
            var result = new ArrayList<MigrationSourceRecordFact>();
            for (var c : page.records()) {
                var record = new MigrationSourceRecordFact((long)sourceRecords.size()+1, c.tenantId(), c.batchId(),
                        c.sourceSystem(), c.sourceTable(), c.sourcePk(), c.sourceBusinessKey(), c.sourcePayloadJson(),
                        c.sourceChecksum(), c.extractedAt(), null);
                sourceRecords.add(record); result.add(record);
            }
            return result;
        });
        when(api.claimStagedBatch(any())).thenAnswer(inv -> {
            ClaimStagedBatchCommand c = inv.getArgument(0);
            var batch = batches.values().stream().filter(b -> !claimed.contains(b.batchId())
                    && b.tenantId().equals(c.tenantId()) && b.ownerContextCode().equals(c.ownerContextCode())
                    && b.purposeCode().equals(c.purposeCode()) && c.sourceSystems().contains(b.sourceSystem())
                    && c.sourceTables().contains(b.sourceTable())).findFirst().orElseThrow();
            claimed.add(batch.batchId());
            return new MigrationBatchClaimResult(true, new MigrationBatchFact(batch.batchId(), batch.tenantId(),
                    batch.ownerContextCode(), batch.purposeCode(), batch.releaseId(), batch.sourceSystem(), batch.sourceTable(),
                    MigrationBatchStatus.RECONCILING, batch.sourceCount(), 0, 0, 0, null, 1, batch.createTime()));
        });
        doAnswer(inv -> { completed.add(inv.getArgument(0)); return null; }).when(api).completeReconciliation(any());
        var objects = caseOnlyNames ? List.of("T_USER_ROLE", "t_user_role") : List.of("DEPARTMENT", "ehr_department");
        var d = SyncDefinition.builder().sourceSystem("DPPMS").adapter("DEPARTMENT")
                .sources(objects.stream().map(o -> SyncDefinition.Source.builder().object(o).sourceObject("ehr_department").sourceKey("id").build()).toList()).build();
        var snapshot = new MysqlSyncReader.Snapshot(LocalDateTime.of(2026,10,15,0,0), objects.stream()
                .map(o -> new MysqlSyncReader.SourceRows(o, "ehr_department", List.of(Map.<String,Object>of("id", 7), Map.<String,Object>of("id", 8)))).toList(), 0);
        var descriptor = new DataSyncAdapter.Descriptor("DEPARTMENT", "Department", objects.stream()
                .map(o -> new DataSyncAdapter.ObjectDescriptor(o, o, List.of(), "SYS", "DEPT", "system_dept", false)).toList(), List.of(), List.of(), false);
        TenantContextHolder.setTenantId(1L);
        try {
            var service = new SyncEvidenceService(api);
            var staged = service.stage(99L, d, snapshot);
            assertEquals(2, staged.stream().map(SyncEvidenceService.Evidence::batchId).distinct().count());
            staged.forEach(e -> assertEquals(Set.of("7", "8"), e.recordIds().keySet()));
            assertTrue(sourceRecords.stream().allMatch(r -> r.sourceTable().equals("ehr_department")));
            assertEquals(List.of("7", "8", "7", "8"), sourceRecords.stream().map(MigrationSourceRecordFact::sourcePk).toList());
            service.complete(99L, d, staged, descriptor, List.of(), false);
            assertEquals(staged.stream().map(SyncEvidenceService.Evidence::batchId).toList(), completed.stream().map(CompleteReconciliationCommand::batchId).toList());
            completed.forEach(c -> { assertEquals(2, c.expectedSourceCount()); assertEquals(2, c.expectedRetainedCount()); });
            var chunk = service.stage(99L, d, snapshot, "99:chunk2", "chunk2");
            assertEquals(4, batches.size());
            assertTrue(Collections.disjoint(staged.stream().map(SyncEvidenceService.Evidence::batchId).toList(), chunk.stream().map(SyncEvidenceService.Evidence::batchId).toList()));
            service.complete(99L, d, chunk, descriptor, List.of(), false);
            assertEquals(4, completed.size());
        } finally { TenantContextHolder.clear(); }
    }
}
