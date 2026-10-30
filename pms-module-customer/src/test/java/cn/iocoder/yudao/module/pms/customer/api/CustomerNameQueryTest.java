package cn.iocoder.yudao.module.pms.customer.api;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi;
import cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.CustomerMasterMapper;
import cn.iocoder.yudao.module.pms.customer.service.query.CustomerQueryService;
import cn.iocoder.yudao.module.pms.customer.service.security.CustomerScopeContextService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerNameQueryTest {
    @Test void nameCandidatesStayInCurrentTenantAndBlankDoesNotListAll() {
        var mapper = mock(CustomerMasterMapper.class);
        var api = new CustomerQueryApiImpl(mapper, mock(CustomerQueryService.class), mock(CustomerScopeContextService.class), mock(PermissionApi.class));
        TenantContextHolder.setTenantId(1L);
        try {
            assertTrue(api.findCustomerIdsByName(new CustomerQueryApi.NameQuery(2L, "名称")).isEmpty());
            assertTrue(api.findCustomerIdsByName(new CustomerQueryApi.NameQuery(1L, "  ")).isEmpty());
            verifyNoInteractions(mapper);
            when(mapper.selectIdsByName(any())).thenReturn(Set.of(10L));
            assertEquals(Set.of(10L), api.findCustomerIdsByName(new CustomerQueryApi.NameQuery(1L, " 名称 ")));
            verify(mapper).selectIdsByName(argThat(q -> q.tenantId().equals(1L) && q.keyword().equals("名称")));
        } finally { TenantContextHolder.clear(); }
    }
}
