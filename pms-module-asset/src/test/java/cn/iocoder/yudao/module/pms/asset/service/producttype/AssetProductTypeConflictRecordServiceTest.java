package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProductTypeConflictRecordServiceTest {

    @Mock private AssetProductTypeSourceMappingMapper sourceMappingMapper;
    @Mock private AssetProductTypeMapper productTypeMapper;
    @Mock private AssetProductTypeAuditService auditService;

    private AssetProductTypeConflictRecordService service;

    @BeforeEach
    void setUp() {
        service = new AssetProductTypeConflictRecordService(sourceMappingMapper, productTypeMapper, auditService);
    }

    @Test
    void shouldMarkCurrentMappingConflictWhenSuccessfulWatermarkHasNotAdvanced() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 8, 30, 9, 0);
        AssetProductTypeSourceMappingDO mapping = mapping(observedAt);
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType("TYPE-B"));
        AssetProductTypeImportRejectedException rejection = AssetProductTypeImportRejectedException.sourceConflict(
                command(LocalDateTime.of(2026, 8, 30, 10, 0)), "TYPE-B", observedAt);

        service.record(1L, 9L, rejection);

        verify(sourceMappingMapper).markConflict(any());
        verify(auditService).recordConflict(1L, 9L, rejection, "TYPE-B",
                "CRM", "source-1", "v1", observedAt);
    }

    @Test
    void shouldPreserveAdvancedSuccessfulFactWhileRecordingOlderConflictSummary() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 8, 30, 9, 0);
        LocalDateTime advancedAt = LocalDateTime.of(2026, 8, 30, 11, 0);
        AssetProductTypeSourceMappingDO mapping = mapping(advancedAt);
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType("TYPE-B"));
        AssetProductTypeImportRejectedException rejection = AssetProductTypeImportRejectedException.sourceConflict(
                command(LocalDateTime.of(2026, 8, 30, 10, 0)), "TYPE-B", observedAt);

        service.record(1L, 9L, rejection);

        assertEquals("RESOLVED", mapping.getMappingStatus());
        assertEquals(advancedAt, mapping.getSourceUpdatedAt());
        assertEquals("v3", mapping.getSourceVersion());
        assertEquals("c".repeat(64), mapping.getPayloadHash());
        assertEquals(11L, mapping.getProductTypeId());
        verify(sourceMappingMapper, never()).markConflict(any());
        verify(auditService).recordConflict(1L, 9L, rejection, "TYPE-B",
                "CRM", "source-1", "v3", advancedAt);
    }

    @Test
    void shouldAuditAdvancedSuccessfulFactWithoutViolatingResolvedMappingConstraint() {
        LocalDateTime observedAt = LocalDateTime.of(2026, 8, 30, 9, 0);
        LocalDateTime advancedAt = LocalDateTime.of(2026, 8, 30, 11, 0);
        AssetProductTypeSourceMappingDO mapping = mapping(advancedAt);
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType("TYPE-B"));
        AssetProductTypeImportRejectedException rejection = AssetProductTypeImportRejectedException.sourceConflict(
                command(LocalDateTime.of(2026, 8, 30, 10, 0)), "TYPE-B", observedAt);

        service.record(1L, 9L, rejection);

        verify(sourceMappingMapper, never()).markConflict(any());
        verify(auditService).recordConflict(1L, 9L, rejection, "TYPE-B",
                "CRM", "source-1", "v3", advancedAt);
    }

    @Test
    void shouldAuditCodeConflictWithCurrentSourceEvidenceWhenIncomingMappingDoesNotExist() {
        LocalDateTime currentAt = LocalDateTime.of(2026, 8, 30, 9, 0);
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(null);
        AssetProductTypeImportRejectedException rejection = AssetProductTypeImportRejectedException.codeConflict(
                command(LocalDateTime.of(2026, 8, 30, 10, 0)), "MES", "existing-source", "current-v1", currentAt);

        service.record(1L, 9L, rejection);

        verify(auditService).recordConflict(1L, 9L, rejection, "TYPE-A",
                "MES", "existing-source", "current-v1", currentAt);
    }

    private AssetProductTypeSourceMappingDO mapping(LocalDateTime sourceUpdatedAt) {
        AssetProductTypeSourceMappingDO mapping = new AssetProductTypeSourceMappingDO();
        mapping.setId(21L);
        mapping.setTenantId(1L);
        mapping.setSourceSystem("CRM");
        mapping.setSourceKey("source-1");
        mapping.setSourceVersion(sourceUpdatedAt.getHour() == 11 ? "v3" : "v1");
        mapping.setSourceUpdatedAt(sourceUpdatedAt);
        mapping.setPayloadHash(sourceUpdatedAt.getHour() == 11 ? "c".repeat(64) : "a".repeat(64));
        mapping.setProductTypeId(11L);
        mapping.setMappingStatus("RESOLVED");
        return mapping;
    }

    private AssetProductTypeDO productType(String code) {
        AssetProductTypeDO productType = new AssetProductTypeDO();
        productType.setId(11L);
        productType.setTypeCode(code);
        return productType;
    }

    private ImportAssetProductTypeCommand command(LocalDateTime sourceUpdatedAt) {
        return new ImportAssetProductTypeCommand(
                "op-1", "idem-1", "TYPE-A", "类型A", true,
                "CRM", "source-1", "v2", sourceUpdatedAt, "b".repeat(64), List.of());
    }
}
