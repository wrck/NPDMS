package cn.iocoder.yudao.module.pms.commerce.service.authorization;

import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 合同管理只使用SYSTEM当前有效公司范围，不从部门或全局角色推导公司。 */
@Component
public class CompanyScopeGuard {

    private final OrganizationScopeApi organizationScopeApi;

    public CompanyScopeGuard(OrganizationScopeApi organizationScopeApi) {
        this.organizationScopeApi = organizationScopeApi;
    }

    public Set<String> activeCompanyCodes(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("actorId必须为正数");
        }
        List<UserCompanyDepartmentScopeRespDTO> scopes = organizationScopeApi.getActiveScopes(actorId);
        if (scopes == null) {
            throw new IllegalStateException("SYSTEM组织范围返回null");
        }
        Set<String> companyCodes = new LinkedHashSet<>();
        for (UserCompanyDepartmentScopeRespDTO scope : scopes) {
            String companyCode = trimToNull(scope == null ? null : scope.getCompanyCode());
            if (companyCode == null) {
                throw new IllegalStateException("SYSTEM组织范围缺少companyCode");
            }
            companyCodes.add(companyCode);
        }
        return Set.copyOf(companyCodes);
    }

    public void requireCompany(Long actorId, String companyCode) {
        String required = trimToNull(companyCode);
        if (required == null || !activeCompanyCodes(actorId).contains(required)) {
            throw new CompanyScopeDeniedException("当前主体不具备目标公司范围");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static final class CompanyScopeDeniedException extends RuntimeException {
        public CompanyScopeDeniedException(String message) {
            super(message);
        }
    }
}
