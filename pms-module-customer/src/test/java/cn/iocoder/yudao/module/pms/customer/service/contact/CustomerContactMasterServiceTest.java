package cn.iocoder.yudao.module.pms.customer.service.contact;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.customer.CustomerMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.ContactHistoryMapper;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.CustomerContactMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerContactMasterServiceTest {
    private final CustomerContactMasterMapper mapper = mock(CustomerContactMasterMapper.class);
    private final ContactHistoryMapper history = mock(ContactHistoryMapper.class);
    private final CustomerQueryService customers = mock(CustomerQueryService.class);
    private final CustomerScopeContextService scopes = mock(CustomerScopeContextService.class);
    private final CustomerContactMasterService service = new CustomerContactMasterService(mapper, history, customers, scopes,
            mock(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.class),
            new ContactDictionaryPolicy(mock(cn.iocoder.yudao.module.system.api.dict.DictDataApi.class)));
    private final CustomerContactMasterService.Actor actor = new CustomerContactMasterService.Actor(1L, 3L);

    @BeforeEach void allowedCustomer() {
        var scope = new CustomerVisibleScope(true, List.of());
        when(scopes.resolve(1L, 3L)).thenReturn(scope);
        var customer = new CustomerMasterDO(); customer.setId(7L); customer.setTenantId(1L); customer.setLifecycleStatus("ENABLED");
        when(customers.get(1L, 7L, scope)).thenReturn(customer);
        when(mapper.selectCustomerForUpdate(any())).thenReturn(customer);
    }

    private ContactMasterWrite write(Integer version) {
        return new ContactMasterWrite(7L, 10L, version,
                new ContactValues("联系人", "运维部", null, "13800138000", null, null, null, "新说明"), false, 0, false);
    }

    @Test void deniedCustomerCannotReadOrMutateContacts() {
        assertThrows(ServiceException.class, () -> service.page(actor, 8L, null, null, new PageParam()));
        verifyNoInteractions(mapper, history);
    }

    @Test void updateRequiresFreshVersionAndPreservesSourceIdentity() {
        var existing = new CustomerContactMasterDO(); existing.setId(10L); existing.setCustomerId(7L);
        existing.setTenantId(1L); existing.setVersion(2); existing.setName("旧姓名");
        when(mapper.selectByRow(any())).thenReturn(existing);
        assertThrows(ServiceException.class, () -> service.update(actor, write(1)));
        verify(mapper, never()).updateById(any(CustomerContactMasterDO.class));
        when(mapper.updateById(any(CustomerContactMasterDO.class))).thenReturn(1);
        var changed = service.update(actor, write(2));
        assertEquals(10L, changed.getId()); assertEquals(7L, changed.getCustomerId());
        assertEquals("旧姓名", existing.getName());
        verify(history).insert(argThat((cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.ContactHistoryDO row) -> row.getCustomerContactId().equals(10L)
                && row.getProjectRelationId() == null && row.getBeforeValues().contains("旧姓名")
                && row.getAfterValues().contains("新说明")));
    }

    @Test void rejectedConcurrentUpdateDoesNotAppendSuccessHistory() {
        var existing = new CustomerContactMasterDO(); existing.setId(10L); existing.setCustomerId(7L);
        existing.setTenantId(1L); existing.setVersion(2);
        when(mapper.selectByRow(any())).thenReturn(existing);
        when(mapper.updateById(any(CustomerContactMasterDO.class))).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.update(actor, write(2)));
        verifyNoInteractions(history);
    }

    @Test void contactsOfDisabledCustomerCanStillBeCleanedUp() {
        var customer = new CustomerMasterDO(); customer.setId(7L); customer.setLifecycleStatus("DISABLED");
        when(customers.get(eq(1L),eq(7L),any())).thenReturn(customer);
        when(mapper.selectCustomerForUpdate(any())).thenReturn(customer);
        var contact = new CustomerContactMasterDO(); contact.setId(10L); contact.setCustomerId(7L); contact.setVersion(2);
        when(mapper.selectByRow(any())).thenReturn(contact); when(mapper.deleteUnreferenced(any())).thenReturn(1);
        service.delete(actor,7L,10L,2);
        verify(mapper).deleteUnreferenced(any());
    }
}
