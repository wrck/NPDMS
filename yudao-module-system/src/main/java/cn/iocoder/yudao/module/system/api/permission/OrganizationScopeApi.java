package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;

import java.util.List;

public interface OrganizationScopeApi {

    List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId);

    boolean hasScope(Long userId, Long companyId, Long departmentId);

}
