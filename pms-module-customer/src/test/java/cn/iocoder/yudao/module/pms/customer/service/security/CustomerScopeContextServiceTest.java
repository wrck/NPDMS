package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerScopeContextServiceTest {

    @Mock
    private PermissionApi permissionApi;
    @Mock
    private RoleApi roleApi;
    @Mock
    private CustomerScopeResolver scopeResolver;
    @InjectMocks
    private CustomerScopeContextService service;

    @Test
    void resolvesRoleIdsAndCodesThroughSystemApis() {
        RoleRespDTO role = new RoleRespDTO();
        role.setId(10L);
        role.setCode("crm_admin");
        when(permissionApi.getRoleIdListByUserId(7L)).thenReturn(Set.of(10L));
        when(roleApi.getRoleList(Set.of(10L))).thenReturn(List.of(role));
        CustomerVisibleScope expected = new CustomerVisibleScope(true, List.of());
        when(scopeResolver.resolve(any())).thenReturn(expected);

        CustomerVisibleScope actual = service.resolve(1L, 7L);

        assertEquals(expected, actual);
        verify(scopeResolver).resolve(new CustomerScopeRequest(
                1L, 7L, Set.of(10L), Set.of("crm_admin"), false));
    }

    @Test
    void missingRoleIdsFailsClosedBeforeRoleLookup() {
        when(permissionApi.getRoleIdListByUserId(7L)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.resolve(1L, 7L));

        verifyNoInteractions(roleApi, scopeResolver);
    }

    @Test
    void missingRoleDetailsFailsClosedBeforeScopeResolution() {
        when(permissionApi.getRoleIdListByUserId(7L)).thenReturn(Set.of(10L));
        when(roleApi.getRoleList(Set.of(10L))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> service.resolve(1L, 7L));

        verifyNoInteractions(scopeResolver);
    }
}
