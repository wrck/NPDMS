package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ASSIGNMENT_REQUEST_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_ORGANIZATION_SCOPE_INVALID;

/** 服务经理候选的 SYSTEM 用户与公司/办事处范围重验。 */
@Service
@RequiredArgsConstructor
public class ProjectServiceManagerCandidateValidator {

    private final AdminUserApi adminUserApi;
    private final DeptApi deptApi;
    private final OrganizationScopeApi organizationScopeApi;

    public void validate(Long managerId, Long companyId, Long departmentId, String departmentCode) {
        if (managerId == null || managerId <= 0 || companyId == null || departmentId == null
                || departmentCode == null || departmentCode.isBlank()) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "服务经理候选范围不完整");
        }
        adminUserApi.validateUser(managerId);
        DeptRespDTO department = deptApi.getDept(departmentId);
        if (department == null || !Objects.equals(department.getCode(), departmentCode)) {
            throw exception(PROJECT_ASSIGNMENT_REQUEST_INVALID, "办事处部门不存在或已停用");
        }
        if (!organizationScopeApi.hasScope(managerId, companyId, departmentId)) {
            throw exception(PROJECT_ORGANIZATION_SCOPE_INVALID, "候选服务经理不具备项目公司与办事处的联合范围");
        }
    }
}
