package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import lombok.RequiredArgsConstructor;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@RequiredArgsConstructor
final class DirectProjectCreationAuthorization {
    private final PermissionCommonApi permissions;
    void assertCanCreate(Long actor) { require(actor,"pms:project:create"); }
    void assertCanImportWithoutOrganization(Long actor) { assertCanImportAcrossOrganizations(actor); }
    void assertCanImportAcrossOrganizations(Long actor) {
        if(!permissions.hasAnyRoles(actor,cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum.SUPER_ADMIN.getCode()))
            throw exception(FORBIDDEN);
        assertCanCreate(actor);require(actor,"pms:integration:execute");
    }
    private void require(Long actor,String permission) {
        if(!permissions.hasAnyPermissions(actor,permission))throw exception(FORBIDDEN);
    }
}
