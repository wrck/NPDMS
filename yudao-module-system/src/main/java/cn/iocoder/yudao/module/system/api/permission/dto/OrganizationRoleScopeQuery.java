package cn.iocoder.yudao.module.system.api.permission.dto;
import java.util.Set;
/** 当前主体在同一组织授权行内匹配指定公司/部门；空部门授权覆盖公司。 */
public record OrganizationRoleScopeQuery(Long tenantId, Long userId, Long companyId,
        Long departmentId, Set<String> requiredRoleCodes) {
    public OrganizationRoleScopeQuery {
        requiredRoleCodes = requiredRoleCodes == null ? Set.of() : Set.copyOf(requiredRoleCodes);
    }
}
