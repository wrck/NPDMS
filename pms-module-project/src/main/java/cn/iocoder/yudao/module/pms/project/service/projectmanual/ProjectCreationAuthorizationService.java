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

    private void assertHasPermission(Long actorId, String permission) {
        if (!permissionApi.hasAnyPermissions(actorId, permission)) {
            throw exception(FORBIDDEN);
        }
    }
}
