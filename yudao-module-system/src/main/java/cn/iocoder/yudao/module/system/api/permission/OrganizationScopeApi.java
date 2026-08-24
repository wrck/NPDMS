package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;

import java.util.List;

public interface OrganizationScopeApi {

    List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId);

    boolean hasScope(Long userId, Long companyId, Long departmentId);

    PageResult<OrganizationUserCandidateRespDTO> pageActiveUsers(OrganizationUserCandidatePageReqDTO request);

}
