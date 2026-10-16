package cn.iocoder.yudao.module.pms.customer.api.masterdata;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.masterdata.dto.CustomerMasterDataCommand;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.*;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerMasterDataErpCodeTest {
    @Test void sourceVersionProtectsErpCodeAndNewerPayloadCanClearIt() {
        TenantContextHolder.setTenantId(1L);
        try {
            var customers=mock(CustomerMasterMapper.class);var mappings=mock(CustomerExternalMappingMapper.class);
            var service=new CustomerMasterDataApiImpl(customers,mappings);
            var customer=new CustomerMasterDO();customer.setId(10L);customer.setVersion(1L);
            customer.setCode("CRM-1");customer.setName("客户");customer.setLifecycleStatus("ENABLED");customer.setErpCustomerCode("ERP-1");
            var mapping=new CustomerExternalMappingDO();mapping.setCustomerId(10L);mapping.setSourceVersion("2026-10-15");
            when(mappings.selectCurrent(any())).thenReturn(mapping);
            when(customers.selectIncludingDeletedForUpdate(1L,10L)).thenReturn(customer);
            when(customers.updateById(customer)).thenReturn(1);
            assertTrue(service.apply(command("ERP-1","2026-10-15")).replayed());
            assertThrows(IllegalStateException.class,()->service.apply(command("ERP-2","2026-10-15")));
            assertEquals("ERP-1",customer.getErpCustomerCode());
            assertTrue(service.apply(command("ERP-2","2026-10-14")).replayed());
            verify(customers,never()).updateById(customer);
            assertFalse(service.apply(command("ERP-2","2026-10-16")).replayed());
            assertEquals("ERP-2",customer.getErpCustomerCode());
            assertFalse(service.apply(command(null,"2026-10-17")).replayed());
            assertNull(customer.getErpCustomerCode());
        } finally {TenantContextHolder.clear();}
    }
    private CustomerMasterDataCommand command(String erpCode,String version) {
        return new CustomerMasterDataCommand(1L,10L,"CRM-1",erpCode,"客户",null,null,null,null,
                null,null,null,null,null,null,null,null,null,null,"ENABLED","SOURCE-1",version,
                LocalDateTime.parse("2026-10-15T12:00:00"),"operation-"+version,null);
    }
}
