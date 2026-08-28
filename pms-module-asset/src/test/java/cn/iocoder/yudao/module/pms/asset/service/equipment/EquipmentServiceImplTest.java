package cn.iocoder.yudao.module.pms.asset.service.equipment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment.EquipmentVersionMapper;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_EQUIPMENT_CUSTOMER_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceImplTest {

    @Mock
    private EquipmentMapper equipmentMapper;
    @Mock
    private EquipmentVersionMapper equipmentVersionMapper;
    @Mock
    private CustomerQueryApi customerQueryApi;

    @InjectMocks
    private EquipmentServiceImpl service;

    @Test
    void disabledCustomerCannotBeAssignedToNewEquipment() {
        EquipmentSaveReqVO request = new EquipmentSaveReqVO();
        request.setCustomerId(1L);
        request.setSerialNumber("SN-001");
        request.setName("设备一");
        when(customerQueryApi.getCustomer(1L)).thenReturn(customer("DISABLED"));

        ServiceException error = assertThrows(ServiceException.class, () -> service.createEquipment(request));

        assertEquals(AST_EQUIPMENT_CUSTOMER_UNAVAILABLE.getCode(), error.getCode());
        verify(equipmentMapper, never()).insert(org.mockito.ArgumentMatchers.<cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO>any());
    }

    private static CustomerSummaryDTO customer(String status) {
        return new CustomerSummaryDTO(1L, 1L, "C-001", "客户一", "客户一", status,
                "PLATFORM_TEMPORARY", 0L, LocalDateTime.of(2026, 8, 25, 12, 0));
    }
}
