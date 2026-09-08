package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserPageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.CompanyRoleUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;

import java.util.List;

public interface OrganizationScopeApi {

    List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId);

    boolean hasScope(Long userId, Long companyId, Long departmentId);

    PageResult<OrganizationUserCandidateRespDTO> pageActiveUsers(OrganizationUserCandidatePageReqDTO request);

    /**
     * PM-01 / INT-09：按公司与角色的同一有效授权行查询用户，跨部门去重分页。
     * 调用方负责功能及业务对象范围授权，租户沿用受信上下文；不会建立项目成员。
     */
    PageResult<CompanyRoleUserRespDTO> pageCompanyRoleUsers(CompanyRoleUserPageReqDTO request);

}
