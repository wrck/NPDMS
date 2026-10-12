package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceException;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MigrationPreviewServiceTest {
    private final MigrationSourceRecordMapper sources = mock(MigrationSourceRecordMapper.class);
    private final ExternalKeyMappingMapper mappings = mock(ExternalKeyMappingMapper.class);
    private final MigrationBatchMapper batches = mock(MigrationBatchMapper.class);
    private final MigrationPreviewService service = new MigrationPreviewService(sources, mappings, batches);
    private final MigrationPreviewCommand command = new MigrationPreviewCommand(1L, "DPPMS", "fb_items",
            List.of(new MigrationPreviewRecordRequest("material-1", "a".repeat(64))));

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var source = new MigrationSourceRecordDO();
        source.setId(11L); source.setBatchId(10L); source.setSourceRecordKey("material-1");
        source.setSourceChecksum("a".repeat(64));
        when(sources.selectListByGlobalIdentity(any())).thenReturn(List.of(source));
        when(sources.selectByKeys(any())).thenReturn(List.of(source));
        var batch = new MigrationBatchDO(); batch.setId(10L);
        when(batches.selectByIdentity(any())).thenReturn(batch);
    }

    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void oneMaterialCanMapToMoreThanOneThousandShipmentsWithoutAmbiguityOrTruncation() {
        var targets = IntStream.range(0, 1001).mapToObj(i -> mapping("PRODUCT_FACT", i, 100L + i)).toList();
        when(mappings.selectListBySourceRecordIds(any())).thenReturn(targets);
        var result = service.previewBatch(new MigrationBatchPreviewCommand(1L, "PROJ", "NATIVE_V4_20_shipment-materials",
                "DPPMS-NATIVE-V4:20:shipment-materials", "DPPMS", "fb_items", command.records()));
        assertEquals(1, result.mappedCount());
        assertEquals(0, result.ambiguousCount());
        assertEquals(MigrationPreviewClassification.MAPPED, result.items().getFirst().classification());
        assertEquals(1001, result.items().getFirst().targets().size());
        assertEquals(targets.stream().map(ExternalKeyMappingDO::getTargetSequence).toList(),
                result.items().getFirst().targets().stream().map(MigrationPreviewTarget::targetSequence).toList());
        verify(sources, never()).selectListByGlobalIdentity(any());
    }

    @Test void twoDifferentTargetsInTheSameRoleAndSequenceRemainAmbiguous() {
        when(mappings.selectListBySourceRecordIds(any())).thenReturn(List.of(
                mapping("PRODUCT_FACT", 0, 100L), mapping("PRODUCT_FACT", 0, 101L),
                mapping("PRODUCT_FACT", 1, 102L)));
        var result = service.preview(command);
        assertEquals(MigrationPreviewClassification.AMBIGUOUS, result.items().getFirst().classification());
        assertEquals(1, result.ambiguousCount());
        assertEquals(0, result.mappedCount());
        assertEquals(3, result.items().getFirst().targets().size());
    }

    @Test void differentRolesCanReuseSequenceWithoutLosingTheirTargets() {
        when(mappings.selectListBySourceRecordIds(any())).thenReturn(List.of(
                mapping("PRIMARY", 0, 100L), mapping("SECONDARY", 0, 101L)));
        var result = service.preview(command);
        assertEquals(MigrationPreviewClassification.MAPPED, result.items().getFirst().classification());
        assertEquals(2, result.items().getFirst().targets().size());
    }

    @Test void repeatedIdenticalTargetInTheSameSlotIsNotAConflict() {
        when(mappings.selectListBySourceRecordIds(any())).thenReturn(List.of(
                mapping("PRODUCT_FACT", 0, 100L), mapping("PRODUCT_FACT", 0, 100L)));
        assertEquals(MigrationPreviewClassification.MAPPED, service.preview(command).items().getFirst().classification());
    }

    @Test void differentChecksumStillReportsConflictDespiteValidMultipleTargets() {
        when(mappings.selectListBySourceRecordIds(any())).thenReturn(List.of(
                mapping("PRODUCT_FACT", 0, 100L), mapping("PRODUCT_FACT", 1, 101L)));
        var changed = new MigrationPreviewCommand(1L, "DPPMS", "fb_items",
                List.of(new MigrationPreviewRecordRequest("material-1", "b".repeat(64))));
        var result = service.preview(changed);
        assertEquals(MigrationPreviewClassification.CHECKSUM_CONFLICT, result.items().getFirst().classification());
        assertEquals(1, result.checksumConflictCount());
        assertEquals(0, result.mappedCount());
        assertTrue(result.items().getFirst().targets().isEmpty());
    }

    @Test void tenantMismatchRejectsBeforeReadingAnyEvidence() {
        TenantContextHolder.setTenantId(2L);
        var error = assertThrows(PlatformMigrationEvidenceException.class, () -> service.preview(command));
        assertEquals(PlatformMigrationEvidenceException.Code.TENANT_CONTEXT_MISMATCH, error.getCode());
        verifyNoInteractions(sources, mappings, batches);
    }

    private static ExternalKeyMappingDO mapping(String role, int sequence, Long targetId) {
        var mapping = new ExternalKeyMappingDO();
        mapping.setSourceRecordId(11L); mapping.setResultType("MAPPED");
        mapping.setTargetContext("AST"); mapping.setTargetObjectType("SHIPMENT");
        mapping.setTargetTable("ast_device_shipment"); mapping.setTargetId(targetId);
        mapping.setTargetRole(role); mapping.setTargetSequence(sequence);
        return mapping;
    }
}
