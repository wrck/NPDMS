package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.RecordAssetProductTypeSourceFailureCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProductTypeSourceFailureWriterTest {

    @Mock private AssetProductTypeSourceMappingMapper sourceMappingMapper;
    @Mock private AssetProductTypeMapper productTypeMapper;
    @Mock private AssetProductTypeAuditService auditService;

    private AssetProductTypeSourceFailureWriter writer;

    @BeforeEach
    void setUp() {
        writer = new AssetProductTypeSourceFailureWriter(sourceMappingMapper, productTypeMapper, auditService);
    }

    @Test
    void shouldPreserveSuccessfulBusinessFactAndWatermarkWhenMarkingFailure() {
        AssetProductTypeSourceMappingDO mapping = new AssetProductTypeSourceMappingDO();
        mapping.setProductTypeId(11L);
        AssetProductTypeDO productType = new AssetProductTypeDO();
        productType.setId(11L);
        productType.setTypeCode("TYPE-A");
        productType.setDisplayName("类型A");
        productType.setSourceVersion("v1");
        productType.setSourceUpdatedAt(LocalDateTime.of(2026, 8, 30, 9, 0));
        productType.setPayloadHash("a".repeat(64));
        productType.setSyncedAt(LocalDateTime.of(2026, 8, 30, 9, 1));
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType);

        writer.markFailed(1L, 9L, new RecordAssetProductTypeSourceFailureCommand(
                "op-1", "CRM", "source-1", "TIMEOUT"));

        assertEquals("FAILED", productType.getSyncStatus());
        assertEquals("TYPE-A", productType.getTypeCode());
        assertEquals("类型A", productType.getDisplayName());
        assertEquals("v1", productType.getSourceVersion());
        assertEquals(LocalDateTime.of(2026, 8, 30, 9, 0), productType.getSourceUpdatedAt());
        assertEquals("a".repeat(64), productType.getPayloadHash());
        assertEquals(LocalDateTime.of(2026, 8, 30, 9, 1), productType.getSyncedAt());
        verify(productTypeMapper).updateById(productType);
        verify(auditService).recordSourceFailure(1L, 9L, "op-1", "CRM", "source-1", "TIMEOUT");
    }

    @Test
    void shouldPreserveSuccessfulCopyWhenSourceReturnsEmptyResponse() {
        AssetProductTypeSourceMappingDO mapping = new AssetProductTypeSourceMappingDO();
        mapping.setProductTypeId(11L);
        AssetProductTypeDO productType = new AssetProductTypeDO();
        productType.setId(11L);
        productType.setTypeCode("TYPE-A");
        productType.setDisplayName("类型A");
        productType.setSourceVersion("v1");
        productType.setSourceUpdatedAt(LocalDateTime.of(2026, 8, 30, 9, 0));
        productType.setPayloadHash("a".repeat(64));
        productType.setSyncedAt(LocalDateTime.of(2026, 8, 30, 9, 1));
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType);

        writer.markFailed(1L, 9L, new RecordAssetProductTypeSourceFailureCommand(
                "op-1", "CRM", "source-1", "EMPTY_RESPONSE"));

        assertEquals("FAILED", productType.getSyncStatus());
        assertEquals("TYPE-A", productType.getTypeCode());
        assertEquals("类型A", productType.getDisplayName());
        assertEquals("v1", productType.getSourceVersion());
        assertEquals(LocalDateTime.of(2026, 8, 30, 9, 0), productType.getSourceUpdatedAt());
        assertEquals("a".repeat(64), productType.getPayloadHash());
        assertEquals(LocalDateTime.of(2026, 8, 30, 9, 1), productType.getSyncedAt());
        verify(productTypeMapper).updateById(productType);
        verify(auditService).recordSourceFailure(
                1L, 9L, "op-1", "CRM", "source-1", "EMPTY_RESPONSE");
    }

    @Test
    void shouldNotCreateGuessedFactWhenSuccessfulCopyDoesNotExist() {
        when(sourceMappingMapper.selectForUpdate(any())).thenReturn(null);

        writer.markFailed(1L, 9L, new RecordAssetProductTypeSourceFailureCommand(
                "op-1", "CRM", "source-1", "EMPTY_RESPONSE"));

        verify(productTypeMapper, never()).selectById(any());
        verify(productTypeMapper, never()).insert(any(AssetProductTypeDO.class));
        verify(productTypeMapper, never()).updateById(any(AssetProductTypeDO.class));
        verify(auditService).recordSourceFailure(1L, 9L, "op-1", "CRM", "source-1", "EMPTY_RESPONSE");
    }
}
