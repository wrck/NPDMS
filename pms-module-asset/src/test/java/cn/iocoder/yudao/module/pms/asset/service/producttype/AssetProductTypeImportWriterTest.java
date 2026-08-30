package cn.iocoder.yudao.module.pms.asset.service.producttype;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.AssetProductTypeSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.producttype.DeviceCurrentProductTypeDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.AssetProductTypeSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.producttype.DeviceCurrentProductTypeMapper;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.DeviceCurrentProductTypeInput;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetProductTypeImportWriterTest {

    @Mock private AssetProductTypeSourceOrder sourceOrder;
    @Mock private AssetProductTypeMapper productTypeMapper;
    @Mock private AssetProductTypeSourceMappingMapper sourceMappingMapper;
    @Mock private DeviceCurrentProductTypeMapper currentProductTypeMapper;
    @Mock private DeviceMapper deviceMapper;

    private AssetProductTypeImportWriter writer;

    @BeforeEach
    void setUp() {
        writer = new AssetProductTypeImportWriter(sourceOrder, productTypeMapper, sourceMappingMapper,
                currentProductTypeMapper, deviceMapper);
    }

    @Test
    void shouldRejectSameSourceDifferentTargetBeforeBusinessWrites() {
        AssetProductTypeSourceMappingDO mapping = mapping("v1", time(9), "a", 11L);
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType(11L, "TYPE-B"));
        when(sourceOrder.decide(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssetProductTypeSourceOrder.Decision.SOURCE_CONFLICT);

        assertThrows(AssetProductTypeImportRejectedException.class,
                () -> writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of())));

        verify(productTypeMapper, never()).selectByCodeForUpdate(any());
        verify(productTypeMapper, never()).insert(any(AssetProductTypeDO.class));
        verify(productTypeMapper, never()).updateById(any(AssetProductTypeDO.class));
        verify(sourceMappingMapper, never()).insert(any(AssetProductTypeSourceMappingDO.class));
        verify(sourceMappingMapper, never()).updateById(any(AssetProductTypeSourceMappingDO.class));
        verifyNoInteractions(currentProductTypeMapper, deviceMapper);
    }

    @Test
    void shouldRejectSoftDeletedSourceKeyBeforeBusinessWrites() {
        AssetProductTypeSourceMappingDO mapping = mapping("v1", time(9), "a", 11L);
        mapping.setDeleted(true);
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(mapping);

        AssetProductTypeImportRejectedException rejection = assertThrows(
                AssetProductTypeImportRejectedException.class,
                () -> writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of())));

        assertEquals("SOURCE_KEY_RESERVED", rejection.rejectionCode());
        verifyNoInteractions(sourceOrder, productTypeMapper, currentProductTypeMapper, deviceMapper);
        verify(sourceMappingMapper, never()).insert(any(AssetProductTypeSourceMappingDO.class));
        verify(sourceMappingMapper, never()).updateById(any(AssetProductTypeSourceMappingDO.class));
    }

    @Test
    void shouldRejectSoftDeletedProductTypeCodeBeforeBusinessWrites() {
        AssetProductTypeDO productType = productType(11L, "TYPE-A");
        productType.setSourceSystem("CRM");
        productType.setSourceKey("source-1");
        productType.setDeleted(true);
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(null);
        when(productTypeMapper.selectByCodeForUpdate(any())).thenReturn(productType);

        AssetProductTypeImportRejectedException rejection = assertThrows(
                AssetProductTypeImportRejectedException.class,
                () -> writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of())));

        assertEquals("PRODUCT_TYPE_CODE_RESERVED", rejection.rejectionCode());
        verify(productTypeMapper, never()).insert(any(AssetProductTypeDO.class));
        verify(productTypeMapper, never()).updateById(any(AssetProductTypeDO.class));
        verifyNoInteractions(currentProductTypeMapper, deviceMapper);
        verify(sourceMappingMapper, never()).insert(any(AssetProductTypeSourceMappingDO.class));
    }

    @Test
    void shouldReturnIdempotentReplayWithoutBusinessWrites() {
        AssetProductTypeSourceMappingDO mapping = mapping("v2", time(10), "b", 11L);
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType(11L, "TYPE-A"));
        when(sourceOrder.decide(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssetProductTypeSourceOrder.Decision.IDEMPOTENT_REPLAY);

        var result = writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of()));

        assertTrue(result.replayed());
        assertEquals(11L, result.productTypeId());
        assertEquals(21L, result.sourceMappingId());
        verify(productTypeMapper, never()).selectByCodeForUpdate(any());
        verify(productTypeMapper, never()).insert(any(AssetProductTypeDO.class));
        verify(productTypeMapper, never()).updateById(any(AssetProductTypeDO.class));
        verify(sourceMappingMapper, never()).insert(any(AssetProductTypeSourceMappingDO.class));
        verify(sourceMappingMapper, never()).updateById(any(AssetProductTypeSourceMappingDO.class));
        verifyNoInteractions(currentProductTypeMapper, deviceMapper);
    }

    @Test
    void shouldRejectStaleSourceWithoutBusinessWrites() {
        AssetProductTypeSourceMappingDO mapping = mapping("v3", time(11), "c", 11L);
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType(11L, "TYPE-A"));
        when(sourceOrder.decide(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssetProductTypeSourceOrder.Decision.STALE_SOURCE);

        AssetProductTypeImportRejectedException rejection = assertThrows(
                AssetProductTypeImportRejectedException.class,
                () -> writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of())));

        assertEquals("STALE_SOURCE", rejection.rejectionCode());
        verify(productTypeMapper, never()).selectByCodeForUpdate(any());
        verify(productTypeMapper, never()).insert(any(AssetProductTypeDO.class));
        verify(productTypeMapper, never()).updateById(any(AssetProductTypeDO.class));
        verify(sourceMappingMapper, never()).insert(any(AssetProductTypeSourceMappingDO.class));
        verify(sourceMappingMapper, never()).updateById(any(AssetProductTypeSourceMappingDO.class));
        verifyNoInteractions(currentProductTypeMapper, deviceMapper);
    }

    @Test
    void shouldCreateCompleteControlledCopyAndSourceMappingOnFirstImport() {
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(null);
        when(productTypeMapper.selectByCodeForUpdate(any())).thenReturn(null);
        when(productTypeMapper.insert(any(AssetProductTypeDO.class))).thenAnswer(invocation -> {
            invocation.<AssetProductTypeDO>getArgument(0).setId(11L);
            return 1;
        });
        when(sourceMappingMapper.insert(any(AssetProductTypeSourceMappingDO.class))).thenAnswer(invocation -> {
            invocation.<AssetProductTypeSourceMappingDO>getArgument(0).setId(21L);
            return 1;
        });
        ArgumentCaptor<AssetProductTypeDO> productCaptor = ArgumentCaptor.forClass(AssetProductTypeDO.class);
        ArgumentCaptor<AssetProductTypeSourceMappingDO> mappingCaptor =
                ArgumentCaptor.forClass(AssetProductTypeSourceMappingDO.class);

        var result = writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of()));

        verify(productTypeMapper).insert(productCaptor.capture());
        verify(sourceMappingMapper).insert(mappingCaptor.capture());
        AssetProductTypeDO productType = productCaptor.getValue();
        AssetProductTypeSourceMappingDO mapping = mappingCaptor.getValue();
        assertFalse(result.replayed());
        assertEquals(1L, productType.getTenantId());
        assertEquals("TYPE-A", productType.getTypeCode());
        assertEquals("类型A", productType.getDisplayName());
        assertTrue(productType.getEnabled());
        assertEquals("CRM", productType.getSourceSystem());
        assertEquals("source-1", productType.getSourceKey());
        assertEquals("v2", productType.getSourceVersion());
        assertEquals(time(10), productType.getSourceUpdatedAt());
        assertEquals("b".repeat(64), productType.getPayloadHash());
        assertEquals("FRESH", productType.getSyncStatus());
        assertNotNull(productType.getSyncedAt());
        assertEquals("RESOLVED", mapping.getMappingStatus());
        assertEquals(11L, mapping.getProductTypeId());
        assertEquals(productType.getSyncedAt(), mapping.getSyncedAt());
    }

    @Test
    void shouldUpdateControlledCopyAndMappingForNewerSource() {
        AssetProductTypeSourceMappingDO mapping = mapping("v1", time(9), "a", 11L);
        AssetProductTypeDO productType = productType(11L, "TYPE-A");
        productType.setSourceSystem("CRM");
        productType.setSourceKey("source-1");
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(mapping);
        when(productTypeMapper.selectById(11L)).thenReturn(productType);
        when(sourceOrder.decide(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssetProductTypeSourceOrder.Decision.NEWER);
        when(productTypeMapper.selectByCodeForUpdate(any())).thenReturn(productType);
        when(productTypeMapper.updateById(productType)).thenReturn(1);
        when(sourceMappingMapper.updateById(mapping)).thenReturn(1);

        var result = writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of()));

        assertFalse(result.replayed());
        assertEquals("类型A", productType.getDisplayName());
        assertTrue(productType.getEnabled());
        assertEquals("v2", productType.getSourceVersion());
        assertEquals(time(10), productType.getSourceUpdatedAt());
        assertEquals("b".repeat(64), productType.getPayloadHash());
        assertEquals("FRESH", productType.getSyncStatus());
        assertEquals("v2", mapping.getSourceVersion());
        assertEquals(time(10), mapping.getSourceUpdatedAt());
        assertEquals("b".repeat(64), mapping.getPayloadHash());
        assertEquals("RESOLVED", mapping.getMappingStatus());
        verify(productTypeMapper).updateById(productType);
        verify(sourceMappingMapper).updateById(mapping);
    }

    @Test
    void shouldRejectCrossTenantDeviceAfterPreparedFactsSoTransactionCanRollBackAllWrites() {
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(null);
        when(productTypeMapper.selectByCodeForUpdate(any())).thenReturn(null);
        when(productTypeMapper.insert(any(AssetProductTypeDO.class))).thenAnswer(invocation -> {
            invocation.<AssetProductTypeDO>getArgument(0).setId(11L);
            return 1;
        });
        when(sourceMappingMapper.insert(any(AssetProductTypeSourceMappingDO.class))).thenAnswer(invocation -> {
            invocation.<AssetProductTypeSourceMappingDO>getArgument(0).setId(21L);
            return 1;
        });
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 31L)).thenReturn(null);

        assertThrows(AssetProductTypeImportRejectedException.class,
                () -> writer.importOnce(1L, 9L, command("TYPE-A", time(10),
                        List.of(new DeviceCurrentProductTypeInput(31L, "RESOLVED")))));

        verify(productTypeMapper).insert(any(AssetProductTypeDO.class));
        verify(sourceMappingMapper).insert(any(AssetProductTypeSourceMappingDO.class));
        verify(currentProductTypeMapper, never()).insert(any(DeviceCurrentProductTypeDO.class));
    }

    @Test
    void shouldCloseAndInsertDeviceCurrentReferencesInDeviceIdOrder() {
        when(sourceMappingMapper.selectForImportUpdate(any())).thenReturn(null);
        when(productTypeMapper.selectByCodeForUpdate(any())).thenReturn(null);
        when(productTypeMapper.insert(any(AssetProductTypeDO.class))).thenAnswer(invocation -> {
            invocation.<AssetProductTypeDO>getArgument(0).setId(11L);
            return 1;
        });
        when(sourceMappingMapper.insert(any(AssetProductTypeSourceMappingDO.class))).thenAnswer(invocation -> {
            invocation.<AssetProductTypeSourceMappingDO>getArgument(0).setId(21L);
            return 1;
        });
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 31L)).thenReturn(device(31L));
        when(deviceMapper.selectByTenantAndIdForUpdate(1L, 32L)).thenReturn(device(32L));
        when(currentProductTypeMapper.selectCurrentForUpdate(any())).thenReturn(current(31L), current(32L));
        when(currentProductTypeMapper.closeCurrent(any())).thenReturn(1);

        writer.importOnce(1L, 9L, command("TYPE-A", time(10), List.of(
                new DeviceCurrentProductTypeInput(32L, "UNKNOWN"),
                new DeviceCurrentProductTypeInput(31L, "RESOLVED"))));

        InOrder order = inOrder(deviceMapper, currentProductTypeMapper);
        order.verify(deviceMapper).selectByTenantAndIdForUpdate(1L, 31L);
        order.verify(currentProductTypeMapper).selectCurrentForUpdate(any());
        order.verify(currentProductTypeMapper).closeCurrent(any());
        order.verify(currentProductTypeMapper).insert(any(DeviceCurrentProductTypeDO.class));
        order.verify(deviceMapper).selectByTenantAndIdForUpdate(1L, 32L);
        order.verify(currentProductTypeMapper).selectCurrentForUpdate(any());
        order.verify(currentProductTypeMapper).closeCurrent(any());
        order.verify(currentProductTypeMapper).insert(any(DeviceCurrentProductTypeDO.class));

        ArgumentCaptor<DeviceCurrentProductTypeDO> inserted = ArgumentCaptor.forClass(DeviceCurrentProductTypeDO.class);
        verify(currentProductTypeMapper, times(2)).insert(inserted.capture());
        assertEquals(31L, inserted.getAllValues().get(0).getDeviceId());
        assertEquals(11L, inserted.getAllValues().get(0).getProductTypeId());
        assertEquals("TYPE-A", inserted.getAllValues().get(0).getProductTypeCode());
        assertEquals(21L, inserted.getAllValues().get(0).getSourceMappingId());
        assertEquals(32L, inserted.getAllValues().get(1).getDeviceId());
        assertNull(inserted.getAllValues().get(1).getProductTypeId());
        assertNull(inserted.getAllValues().get(1).getProductTypeCode());
        assertEquals(21L, inserted.getAllValues().get(1).getSourceMappingId());
    }

    private ImportAssetProductTypeCommand command(String code, LocalDateTime sourceUpdatedAt,
                                                   List<DeviceCurrentProductTypeInput> devices) {
        return new ImportAssetProductTypeCommand(
                "op-1", "idem-1", code, "类型A", true,
                "CRM", "source-1", "v2", sourceUpdatedAt, "b".repeat(64), devices);
    }

    private AssetProductTypeSourceMappingDO mapping(String version, LocalDateTime updatedAt,
                                                     String hashCharacter, Long productTypeId) {
        AssetProductTypeSourceMappingDO mapping = new AssetProductTypeSourceMappingDO();
        mapping.setId(21L);
        mapping.setTenantId(1L);
        mapping.setSourceSystem("CRM");
        mapping.setSourceKey("source-1");
        mapping.setSourceVersion(version);
        mapping.setSourceUpdatedAt(updatedAt);
        mapping.setPayloadHash(hashCharacter.repeat(64));
        mapping.setProductTypeId(productTypeId);
        mapping.setMappingStatus("RESOLVED");
        return mapping;
    }

    private AssetProductTypeDO productType(Long id, String code) {
        AssetProductTypeDO productType = new AssetProductTypeDO();
        productType.setId(id);
        productType.setTenantId(1L);
        productType.setTypeCode(code);
        return productType;
    }

    private DeviceDO device(Long id) {
        DeviceDO device = new DeviceDO();
        device.setId(id);
        device.setTenantId(1L);
        return device;
    }

    private DeviceCurrentProductTypeDO current(Long deviceId) {
        DeviceCurrentProductTypeDO current = new DeviceCurrentProductTypeDO();
        current.setDeviceId(deviceId);
        return current;
    }

    private LocalDateTime time(int hour) {
        return LocalDateTime.of(2026, 8, 30, hour, 0);
    }
}
