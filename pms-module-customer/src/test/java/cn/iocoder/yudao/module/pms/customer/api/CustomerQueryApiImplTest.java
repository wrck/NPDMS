package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerQueryApiImplTest {

    @Mock
    private CustomerMasterMapper customerMasterMapper;

    @InjectMocks
    private CustomerQueryApiImpl api;

    @Test
    void returnsCurrentCustomerSummary() {
        CustomerMasterDO customer = customer(1L, "C-001", "客户一");
        when(customerMasterMapper.selectById(1L)).thenReturn(customer);

        CustomerSummaryDTO result = api.getCustomer(1L);

        assertEquals(1L, result.id());
        assertEquals("C-001", result.code());
        assertEquals("客户一", result.name());
        assertEquals("ENABLED", result.lifecycleStatus());
    }

    @Test
    void returnsNullForMissingOrDeletedCustomer() {
        CustomerMasterDO deleted = customer(2L, "C-002", "客户二");
        deleted.setLifecycleStatus("DELETED");
        when(customerMasterMapper.selectById(404L)).thenReturn(null);
        when(customerMasterMapper.selectById(2L)).thenReturn(deleted);

        assertNull(api.getCustomer(null));
        assertNull(api.getCustomer(404L));
        assertNull(api.getCustomer(2L));
    }

    @Test
    void returnsBatchInRequestedCustomerSet() {
        CustomerMasterDO first = customer(1L, "C-001", "客户一");
        CustomerMasterDO second = customer(2L, "C-002", "客户二");
        when(customerMasterMapper.selectByIds(List.of(1L, 2L))).thenReturn(List.of(first, second));

        List<CustomerSummaryDTO> result = api.getCustomers(List.of(1L, 2L));

        assertEquals(List.of(1L, 2L), result.stream().map(CustomerSummaryDTO::id).toList());
    }

    private static CustomerMasterDO customer(Long id, String code, String name) {
        CustomerMasterDO customer = new CustomerMasterDO();
        customer.setId(id);
        customer.setTenantId(1L);
        customer.setCode(code);
        customer.setName(name);
        customer.setShortName(name);
        customer.setLifecycleStatus("ENABLED");
        customer.setSourceType("PLATFORM_TEMPORARY");
        customer.setVersion(0);
        customer.setDataAsOf(LocalDateTime.of(2026, 8, 25, 12, 0));
        return customer;
    }
}
