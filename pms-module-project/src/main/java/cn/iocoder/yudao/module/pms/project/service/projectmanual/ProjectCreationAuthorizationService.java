package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** F-PROJ-001创建与服务经理指派的服务端授权入口。 */
@Service
public class ProjectCreationAuthorizationService {

    private static final String CREATE_PERMISSION = "pms:project:create";
    private static final String ASSIGN_PERMISSION = "pms:project:assign";

    @Resource
    private PermissionCommonApi permissionApi;

    public void assertCanCreate(Long actorId) {
        assertHasPermission(actorId, CREATE_PERMISSION);
    }

    public void assertCanAssign(Long actorId) {
        assertHasPermission(actorId, ASSIGN_PERMISSION);
    }

    /** Approved historical import exception; does not grant an organization or project scope. */
    public void assertCanImportWithoutOrganization(Long actorId) {
        assertMigrationAdministrator(actorId);
    }

    /** Migration Owner only; ordinary project creation still requires its organization scope. */
    public void assertCanImportAcrossOrganizations(Long actorId) {
        assertMigrationAdministrator(actorId);
    }

    private void assertMigrationAdministrator(Long actorId) {
        if (!permissionApi.hasAnyRoles(actorId,
                cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode())) {
            throw exception(FORBIDDEN);
        }
        assertHasPermission(actorId, CREATE_PERMISSION);
        assertHasPermission(actorId, "pms:integration:execute");
    }

    private void assertHasPermission(Long actorId, String permission) {
        if (!permissionApi.hasAnyPermissions(actorId, permission)) {
            throw exception(FORBIDDEN);
        }
    }
}
