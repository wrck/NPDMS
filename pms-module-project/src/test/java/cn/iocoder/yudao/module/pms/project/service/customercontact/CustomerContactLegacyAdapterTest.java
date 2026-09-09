package cn.iocoder.yudao.module.pms.project.service.customercontact;

import cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.customercontact.vo.CustomerContactSaveReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerContactLegacyAdapterTest {
    @Test void originalReadAndWriteEntryPointsUseTheCustomerOwnedMaster() {
        var api = mock(CustomerContactMasterApi.class);
        var service = new CustomerContactServiceImpl(); ReflectionTestUtils.setField(service, "contacts", api);
        var record = new CustomerContactMasterApi.Contact(1016L, 1001L, "客户主档姓名", null, null,
                "13800138010", null, null, false, 0, null, 2, null, "客户名称");
        when(api.get(1016L)).thenReturn(record);
        assertEquals("客户主档姓名", service.getCustomerContact(1016L).getName());
        var page = new CustomerContactPageReqVO(); page.setPageNo(1); page.setPageSize(10);
        when(api.page(any())).thenReturn(new CustomerContactMasterApi.ContactPage(List.of(record), 1L));
        assertEquals(1016L, service.getCustomerContactPage(page).getList().getFirst().getId());
        service.deleteCustomerContact(1016L); verify(api).delete(1016L, 2);
        var save = new CustomerContactSaveReqVO(); save.setCustomerId(1001L); save.setName("新联系人");
        save.setStatus(0); save.setPrimaryFlag(false); save.setMobile("13800138011");
        when(api.create(any(), anyString())).thenReturn(1017L);
        assertEquals(1017L, service.createCustomerContact(save));
        verify(api).create(argThat(command -> command.customerId().equals(1001L)
                && command.details().name().equals("新联系人")), anyString());
    }
}
