package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCreationAuthorizationServiceTest {

    @Mock
    private PermissionCommonApi permissionApi;

    @InjectMocks
    private ProjectCreationAuthorizationService service;

    @Test
    void createRequiresCreatePermission() {
        when(permissionApi.hasAnyPermissions(7L, "pms:project:create")).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.assertCanCreate(7L));

        assertEquals(FORBIDDEN.getCode(), exception.getCode());
        verify(permissionApi).hasAnyPermissions(7L, "pms:project:create");
    }

    @Test
    void createAcceptsCreatePermission() {
        when(permissionApi.hasAnyPermissions(7L, "pms:project:create")).thenReturn(true);

        service.assertCanCreate(7L);

        verify(permissionApi).hasAnyPermissions(7L, "pms:project:create");
    }

    @Test
    void assignmentRequiresAssignPermission() {
        when(permissionApi.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class, () -> service.assertCanAssign(7L));

        assertEquals(FORBIDDEN.getCode(), exception.getCode());
        verify(permissionApi).hasAnyPermissions(7L, "pms:project:assign");
    }

    @Test
    void assignmentAcceptsAssignPermission() {
        when(permissionApi.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);

        service.assertCanAssign(7L);

        verify(permissionApi).hasAnyPermissions(7L, "pms:project:assign");
    }
}
