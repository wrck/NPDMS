package cn.iocoder.yudao.module.pms.engineering.service.materialexchange;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.SelectedProjectDevice;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSerialVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeSerialDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.materialexchange.MaterialExchangeSerialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialExchangeSerialServiceTest {
    private final MaterialExchangeMapper mapper = mock(MaterialExchangeMapper.class);
    private final MaterialExchangeSerialMapper serialMapper = mock(MaterialExchangeSerialMapper.class);
    private final ProjectDeviceSelectionApi devices = mock(ProjectDeviceSelectionApi.class);
    private final MaterialExchangeServiceImpl service = new MaterialExchangeServiceImpl();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "materialExchangeMapper", mapper);
        ReflectionTestUtils.setField(service, "serialMapper", serialMapper);
        ReflectionTestUtils.setField(service, "deviceSelectionApi", devices);
        when(devices.validateSelection(10L, List.of(1L, 2L))).thenReturn(List.of(
                new SelectedProjectDevice(1L, "REAL-1", "设备一", "P1", "M1", "C1"),
                new SelectedProjectDevice(2L, "REAL-2", "设备二", "P2", "M2", "C1")));
        when(mapper.insert(any(MaterialExchangeDO.class))).thenAnswer(call -> {
            call.getArgument(0, MaterialExchangeDO.class).setId(100L); return 1;
        });
        var existing = new MaterialExchangeDO(); existing.setId(100L); existing.setProjectId(10L);
        existing.setCode("EX-1"); existing.setStatus(0); existing.setVersion(3);
        when(mapper.selectByIdForUpdate(100L)).thenReturn(existing);
        when(mapper.updateById(any(MaterialExchangeDO.class))).thenReturn(1);
    }

    @Test void savesEverySelectedSerialUsingServerSnapshot() {
        assertEquals(100L, service.createMaterialExchange(request()));
        var capture = ArgumentCaptor.forClass(MaterialExchangeSerialDO.class);
        verify(serialMapper, times(2)).insert(capture.capture());
        assertEquals(List.of("REAL-1", "REAL-2"), capture.getAllValues().stream().map(MaterialExchangeSerialDO::getSn).toList());
        assertTrue(capture.getAllValues().stream().allMatch(row -> row.getExchangeId().equals(100L)));
    }

    @Test void invalidSelectionNeverWritesApplicationOrChildren() {
        when(devices.validateSelection(any(), any())).thenThrow(new IllegalArgumentException("out of scope"));
        assertThrows(IllegalArgumentException.class, () -> service.createMaterialExchange(request()));
        verifyNoInteractions(mapper, serialMapper);
    }

    @Test void staleVersionCannotReplaceSerials() {
        var request = request(); request.setId(100L); request.setVersion(2);
        assertThrows(ServiceException.class, () -> service.updateMaterialExchange(request));
        verifyNoInteractions(serialMapper, devices);
    }

    @Test void optimisticWriteFailureCannotReplaceSerials() {
        when(mapper.updateById(any(MaterialExchangeDO.class))).thenReturn(0);
        var request = request(); request.setId(100L); request.setVersion(3);
        assertThrows(ServiceException.class, () -> service.updateMaterialExchange(request));
        verifyNoInteractions(serialMapper);
    }

    @Test void explicitEditReplacesChildrenAfterParentVersionCheck() {
        var request = request(); request.setId(100L); request.setVersion(3);
        service.updateMaterialExchange(request);
        var order = inOrder(mapper, serialMapper);
        order.verify(mapper).selectByIdForUpdate(100L);
        order.verify(mapper).updateById(any(MaterialExchangeDO.class));
        order.verify(serialMapper).deleteByExchange(any());
        order.verify(serialMapper, times(2)).insert(any(MaterialExchangeSerialDO.class));
    }

    @Test void completedApplicationCannotRewriteSnapshots() {
        var row = mapper.selectByIdForUpdate(100L); row.setStatus(3);
        var request = request(); request.setId(100L); request.setVersion(3);
        assertThrows(ServiceException.class, () -> service.updateMaterialExchange(request));
        verifyNoInteractions(serialMapper, devices);
    }

    @Test void quantityMustMatchSerialCount() {
        var request = request(); request.setQuantity(BigDecimal.ONE);
        assertThrows(ServiceException.class, () -> service.createMaterialExchange(request));
        verifyNoInteractions(mapper, serialMapper);
    }

    @Test void legacyClientOmittingChildrenPreservesExistingSnapshot() {
        var row = new MaterialExchangeSerialDO(); row.setEquipmentId(1L); row.setSn("ORIGINAL-SNAPSHOT");
        when(serialMapper.selectByExchange(any())).thenReturn(List.of(row));
        when(devices.validateSelection(10L, List.of(1L))).thenReturn(List.of(
                new SelectedProjectDevice(1L, "CURRENT-SN", null, null, null, null)));
        var request = request(); request.setId(100L); request.setVersion(3); request.setSerials(null);
        service.updateMaterialExchange(request);
        verify(serialMapper, never()).deleteByExchange(any());
        verify(serialMapper, never()).insert(any(MaterialExchangeSerialDO.class));
    }

    private MaterialExchangeSaveReqVO request() {
        var request = new MaterialExchangeSaveReqVO(); request.setProjectId(10L); request.setCode("EX-1");
        request.setQuantity(BigDecimal.valueOf(2));
        var first = new MaterialExchangeSerialVO(); first.setEquipmentId(1L); first.setSn("FORGED-SN");
        var second = new MaterialExchangeSerialVO(); second.setEquipmentId(2L);
        request.setSerials(List.of(first, second)); return request;
    }
}
