package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.HIDDEN;
import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.MASKED;
import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.RAW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerContactAccessServiceTest {

    @Mock
    private PermissionApi permissionApi;
    @InjectMocks
    private CustomerContactAccessService service;

    @Test
    void sensitiveReadPermissionReturnsRawAccess() {
        when(permissionApi.hasAnyPermissions(7L, "pms:customer:sensitive-read"))
                .thenReturn(true);

        assertEquals(RAW, service.resolve(7L, true));
    }

    @Test
    void ordinaryQueryReturnsMaskedAccess() {
        when(permissionApi.hasAnyPermissions(7L, "pms:customer:sensitive-read"))
                .thenReturn(false);

        assertEquals(MASKED, service.resolve(7L, true));
    }

    @Test
    void deniedFieldAccessReturnsHiddenWithoutCheckingSensitivePermission() {
        assertEquals(HIDDEN, service.resolve(7L, false));
    }
}
