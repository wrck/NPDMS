package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.contact.CustomerContactMasterApi;
import cn.iocoder.yudao.module.pms.customer.dal.dataobject.contact.CustomerContactMasterDO;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.contact.CustomerContactMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.contact.CustomerContactMasterService;
import cn.iocoder.yudao.module.pms.customer.service.security.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CustomerContactMasterApiImplTest {
    @Test void noVisibleCustomerScopeReturnsEmptyWithoutQueryingContacts() {
        var mapper = mock(CustomerContactMasterMapper.class); var scopes = mock(CustomerScopeContextService.class);
        when(scopes.resolve(eq(1L), any())).thenReturn(new CustomerVisibleScope(false, List.of()));
        TenantContextHolder.setTenantId(1L);
        try {
            var api = new CustomerContactMasterApiImpl(mock(CustomerContactMasterService.class),mapper,scopes,
                    mock(CustomerContactAccessService.class),new CustomerFieldMaskingService());
            assertEquals(0L, api.page(new CustomerContactMasterApi.PageQuery(null,null,null,null,1,10)).total());
            verifyNoInteractions(mapper);
        } finally { TenantContextHolder.clear(); }
    }
    @Test void legacyAndCurrentConsumersReceiveMaskedContactFieldsWithoutSensitiveRead() {
        var mapper = mock(CustomerContactMasterMapper.class); var scopes = mock(CustomerScopeContextService.class);
        var access = mock(CustomerContactAccessService.class);
        when(scopes.resolve(eq(1L), any())).thenReturn(new CustomerVisibleScope(true, List.of()));
        when(access.resolve(any(), eq(true))).thenReturn(CustomerFieldMaskingService.ContactAccess.MASKED);
        when(mapper.selectVisibleCount(any())).thenReturn(1L);
        var row = new CustomerContactMasterDO(); row.setId(9L); row.setCustomerId(8L); row.setName("联系人");
        row.setMobile("13800138000"); row.setEmail("contact@example.com");
        when(mapper.selectVisiblePage(any())).thenReturn(List.of(row));
        TenantContextHolder.setTenantId(1L);
        try {
            var api = new CustomerContactMasterApiImpl(mock(CustomerContactMasterService.class),mapper,scopes,access,new CustomerFieldMaskingService());
            var result = api.page(new CustomerContactMasterApi.PageQuery(null,null,null,null,1,10)).list().getFirst();
            assertEquals("138****8000", result.mobile()); assertEquals("c****@example.com", result.email());
            assertEquals("13800138000", row.getMobile());
        } finally { TenantContextHolder.clear(); }
    }
}
