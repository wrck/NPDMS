package cn.iocoder.yudao.framework.common.biz.system.permission;

import java.util.Map;
import java.util.Set;

/** Read-only permission display: each code is evaluated separately in the current tenant. */
public interface PermissionBatchQueryApi {
    /** One fresh observation per call; never retain these answers to authorize a later command. */
    Map<String, Boolean> queryPermissions(Long userId, Set<String> permissionCodes);
}
