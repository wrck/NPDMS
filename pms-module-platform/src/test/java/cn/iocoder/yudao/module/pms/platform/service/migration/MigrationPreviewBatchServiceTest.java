package cn.iocoder.yudao.module.pms.platform.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.migration.*;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MigrationPreviewBatchServiceTest {
    MigrationSourceRecordMapper sources=mock(MigrationSourceRecordMapper.class);
    ExternalKeyMappingMapper mappings=mock(ExternalKeyMappingMapper.class);
    MigrationBatchMapper batches=mock(MigrationBatchMapper.class);
    MigrationPreviewService service=new MigrationPreviewService(sources,mappings,batches);
    MigrationBatchPreviewCommand command=new MigrationBatchPreviewCommand(1L,"PROJ","NATIVE_20_devices","RELEASE",
            "DPPMS","SOURCE",List.of(new MigrationPreviewRecordRequest("101:20:30","a".repeat(64))));
    @BeforeEach void setup() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void absentSnapshotBatchDoesNotScanUnrelatedHistoricalSources() {
        var result=service.previewBatch(command);
        assertEquals(MigrationPreviewClassification.NO_EXISTING_SOURCE,result.items().getFirst().classification());
        verifyNoInteractions(sources,mappings);
    }
    @Test void exactSnapshotBatchReadsOnlyItsSourceIdentitiesAndMappings() {
        var batch=new MigrationBatchDO();batch.setId(10L);when(batches.selectByIdentity(any())).thenReturn(batch);
        var source=new MigrationSourceRecordDO();source.setId(11L);source.setBatchId(10L);source.setSourceRecordKey("101:20:30");
        source.setSourceChecksum("a".repeat(64));when(sources.selectByKeys(any())).thenReturn(List.of(source));
        var mapping=new ExternalKeyMappingDO();mapping.setSourceRecordId(11L);mapping.setResultType("MAPPED");
        mapping.setTargetContext("AST");mapping.setTargetObjectType("Device");mapping.setTargetTable("ast_device");
        mapping.setTargetId(100L);mapping.setTargetRole("DEVICE");mapping.setTargetSequence(0);
        when(mappings.selectListBySourceRecordIds(any())).thenReturn(List.of(mapping));
        var result=service.previewBatch(command);
        assertEquals(MigrationPreviewClassification.MAPPED,result.items().getFirst().classification());
        assertEquals(10L,result.items().getFirst().latestBatchId());
        verify(batches).selectByIdentity(argThat(q -> q.tenantId()==1L && q.purposeCode().equals(command.purposeCode())
                && q.releaseId().equals(command.releaseId()) && q.sourceTable().equals("SOURCE")));
        verify(sources).selectByKeys(argThat(q -> q.tenantId()==1L && q.batchId()==10L && q.sourceKeys().equals(List.of("101:20:30"))));
        verify(sources,never()).selectListByGlobalIdentity(any());
    }
    @Test void wrongTrustedTenantStopsEveryEvidenceRead() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class,()->service.previewBatch(command));
        verifyNoInteractions(batches,sources,mappings);
    }
}
