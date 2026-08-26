package cn.iocoder.yudao.module.pms.asset.api.customer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceReferenceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.query.CustomerDeviceSummaryPageQuery;
import cn.iocoder.yudao.module.pms.customer.api.enums.CustomerReferenceGuardStatus;
import cn.iocoder.yudao.module.pms.customer.api.guard.dto.CustomerReferenceGuardQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetCustomerApiImplTest {

    @Mock
    private EquipmentMapper equipmentMapper;
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
    void returnsDeviceSummaryPage() {
        EquipmentDO equipment = new EquipmentDO();
        equipment.setId(20L);
        equipment.setSerialNumber("SN-20");
        equipment.setName("设备二十");
        equipment.setStatus(1);
        when(equipmentMapper.selectCustomerSummaryPage(
                new CustomerDeviceSummaryPageQuery(1L, 100L, 1, 20)))
                .thenReturn(new PageResult<>(List.of(equipment), 1L));

        var result = summaryApi.query(new CustomerDeviceSummaryQuery(1L, 100L, 1, 20));

        assertTrue(result.available());
        assertEquals("AST", result.provider());
        assertEquals(1L, result.total());
        assertEquals(new CustomerDeviceSummaryItem(20L, "SN-20", "设备二十", "1"), result.items().getFirst());
    }
}
