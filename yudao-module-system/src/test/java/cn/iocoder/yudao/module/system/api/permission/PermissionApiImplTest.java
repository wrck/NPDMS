package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionApiImplTest {

    @Mock
    private PermissionService permissionService;
    @InjectMocks
    private PermissionApiImpl permissionApi;

    @Test
    void returnsCachedRoleIdsForUser() {
        when(permissionService.getUserRoleIdListByUserIdFromCache(7L))
                .thenReturn(Set.of(10L, 11L));

        Set<Long> result = permissionApi.getRoleIdListByUserId(7L);

        assertEquals(Set.of(10L, 11L), result);
        verify(permissionService).getUserRoleIdListByUserIdFromCache(7L);
    }
}
