package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerSummaryDTO;
import cn.iocoder.yudao.module.pms.customer.api.query.dto.CustomerCodeQuery;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerVisibleScope;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CustomerQueryApiImplTest {

    @Mock
    private CustomerMasterMapper customerMasterMapper;
    @Mock private CustomerQueryService customerQueryService;
    @Mock private CustomerScopeContextService scopeContextService;
    @Mock private PermissionApi permissionApi;

    @InjectMocks
    private CustomerQueryApiImpl api;

    @AfterEach
    void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void codeLookupUsesTrustedTenantAndActorScope() {
        TenantContextHolder.setTenantId(1L);
        var scope = new CustomerVisibleScope(true, List.of());
        when(permissionApi.hasAnyPermissions(7L, "pms:customer:query")).thenReturn(true);
        when(scopeContextService.resolve(1L, 7L)).thenReturn(scope);
        when(customerQueryService.getByCode(1L, "C-001", scope)).thenReturn(customer(1L, "C-001", "客户一"));
        assertEquals("C-001", api.getCustomerByCode(new CustomerCodeQuery("C-001", 7L)).code());
        verifyNoInteractions(customerMasterMapper);
    }

    @Test
    void codeLookupWithoutQueryPermissionDoesNotReadCustomerData() {
        TenantContextHolder.setTenantId(1L);
        assertNull(api.getCustomerByCode(new CustomerCodeQuery("C-001", 7L)));
        verifyNoInteractions(customerQueryService, scopeContextService, customerMasterMapper);
    }

    @Test
    void referenceLookupLocksTheVisibleVersionAndRejectsAChangedMaster() {
        TenantContextHolder.setTenantId(1L);
        var scope = new CustomerVisibleScope(true, List.of());
        when(permissionApi.hasAnyPermissions(7L, "pms:customer:query")).thenReturn(true);
        when(scopeContextService.resolve(1L, 7L)).thenReturn(scope);
        var visible = customer(1L, "C-001", "客户一");
        var locked = customer(1L, "C-001", "客户一");
        when(customerQueryService.getByCode(1L, "C-001", scope)).thenReturn(visible);
        when(customerMasterMapper.selectIncludingDeletedForUpdate(1L, 1L)).thenReturn(locked);
        assertEquals("C-001", api.lockCustomerByCode(new CustomerCodeQuery("C-001", 7L)).code());
        locked.setVersion(1);
        locked.setLifecycleStatus("DISABLED");
        assertNull(api.lockCustomerByCode(new CustomerCodeQuery("C-001", 7L)));
    }

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
