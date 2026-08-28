package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.CustomerDeviceSummaryPageQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceReferenceQuery;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetCustomerApiImplTest {

    @Mock
    private EquipmentMapper equipmentMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private DeviceAccessScopeService accessScopeService;
    @InjectMocks
    private AssetCustomerReferenceGuardApiImpl guardApi;
    @InjectMocks
    private AssetCustomerDeviceSummaryApiImpl summaryApi;

    @Test
    void reportsReferencedDevices() {
        var query = new CustomerReferenceGuardQuery(1L, 100L);
        when(equipmentMapper.selectCountByCustomer(
                new CustomerDeviceReferenceQuery(1L, 100L)))
                .thenReturn(3L);

        var result = guardApi.check(query);

        assertEquals(CustomerReferenceGuardStatus.REFERENCED.name(), result.status());
        assertEquals("AST", result.provider());
        assertEquals(3L, result.referenceCount());
    }

    @Test
    void returnsDeviceSummaryPageFromAstDevice() {
        DeviceDO device = new DeviceDO();
        device.setId(20L);
        device.setSn("SN-20");
        device.setName("设备二十");
        device.setStatus("ONLINE");
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        when(deviceMapper.selectCustomerSummaryPage(
                new CustomerDeviceSummaryPageQuery(1L, 100L, Set.of(10L), 1, 20)))
                .thenReturn(new PageResult<>(List.of(device), 1L));

        var result = summaryApi.query(new CustomerDeviceSummaryQuery(1L, 100L, 7L, 1, 20));

        assertTrue(result.available());
        assertEquals("AST", result.provider());
        assertEquals(1L, result.total());
        assertEquals(new CustomerDeviceSummaryItem(20L, "SN-20", "设备二十", "ONLINE"), result.items().getFirst());
    }

    @Test
    void returnsAvailableEmptyDeviceSummaryPage() {
        when(accessScopeService.visibleProjectIds(1L, 7L)).thenReturn(Set.of(10L));
        when(deviceMapper.selectCustomerSummaryPage(
                new CustomerDeviceSummaryPageQuery(1L, 100L, Set.of(10L), 1, 20)))
                .thenReturn(PageResult.empty());

        var result = summaryApi.query(new CustomerDeviceSummaryQuery(1L, 100L, 7L, 1, 20));

        assertTrue(result.available());
        assertEquals("AST", result.provider());
        assertTrue(result.items().isEmpty());
        assertEquals(0L, result.total());
    }
}
