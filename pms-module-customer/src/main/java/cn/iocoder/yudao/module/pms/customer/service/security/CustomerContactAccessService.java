package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.HIDDEN;
import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.MASKED;
import static cn.iocoder.yudao.module.pms.customer.service.security.CustomerFieldMaskingService.ContactAccess.RAW;

@Service
public class CustomerContactAccessService {

    private static final String SENSITIVE_READ_PERMISSION = "pms:customer:sensitive-read";

    @Resource
    private PermissionApi permissionApi;

    public CustomerFieldMaskingService.ContactAccess resolve(Long userId, boolean fieldVisible) {
        if (userId == null) {
            throw new IllegalArgumentException("客户联系方式权限主体不能为空");
        }
        if (!fieldVisible) {
            return HIDDEN;
        }
        return permissionApi.hasAnyPermissions(userId, SENSITIVE_READ_PERMISSION) ? RAW : MASKED;
    }
}
