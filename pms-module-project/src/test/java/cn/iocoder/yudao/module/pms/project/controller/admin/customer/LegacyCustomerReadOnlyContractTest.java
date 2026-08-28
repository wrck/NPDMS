package cn.iocoder.yudao.module.pms.project.controller.admin.customer;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customer.vo.CustomerSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.customer.CustomerDO;
import cn.iocoder.yudao.module.pms.project.service.customer.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.CUSTOMER_LEGACY_ROUTE_READ_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyCustomerReadOnlyContractTest {

    @Mock
    private CustomerService customerService;

    private CustomerController controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerController();
        ReflectionTestUtils.setField(controller, "customerService", customerService);
    }

    @Test
    void legacyWritesReturnStableRetirementError() {
        ServiceException create = assertThrows(ServiceException.class,
                () -> controller.createCustomer(new CustomerSaveReqVO()));
        ServiceException update = assertThrows(ServiceException.class,
                () -> controller.updateCustomer(new CustomerSaveReqVO()));
        ServiceException delete = assertThrows(ServiceException.class,
                () -> controller.deleteCustomer(1L));

        assertEquals(CUSTOMER_LEGACY_ROUTE_READ_ONLY.getCode(), create.getCode());
        assertEquals(CUSTOMER_LEGACY_ROUTE_READ_ONLY.getCode(), update.getCode());
        assertEquals(CUSTOMER_LEGACY_ROUTE_READ_ONLY.getCode(), delete.getCode());
        assertEquals(true, create.getMessage().contains("/pms/customers"));
    }

    @Test
    void legacyReadsRemainAvailableAndMarkedReadOnly() {
        CustomerDO customer = new CustomerDO();
        customer.setId(1L);
        when(customerService.getCustomer(1L)).thenReturn(customer);
        when(customerService.getCustomerPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(customer), 1L));

        var detail = controller.getCustomer(1L).getData();
        var page = controller.getCustomerPage(new CustomerPageReqVO()).getData();

        assertEquals(true, detail.getLegacyReadOnly());
        assertEquals("/pms/customers", detail.getReplacementPath());
        assertEquals(true, page.getList().getFirst().getLegacyReadOnly());
        assertEquals("/pms/customers", page.getList().getFirst().getReplacementPath());
    }

    @Test
    void missingLegacyDetailReturnsEmptyData() {
        when(customerService.getCustomer(404L)).thenReturn(null);

        assertNull(controller.getCustomer(404L).getData());
    }
}
