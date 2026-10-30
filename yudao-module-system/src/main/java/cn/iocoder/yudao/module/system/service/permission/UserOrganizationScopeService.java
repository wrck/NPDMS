package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope.*;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface UserOrganizationScopeService {
    PageResult<UserCompanyDepartmentScopeDO> getPage(@Valid @NotNull UserOrganizationScopePageReqVO request);
    Long create(@Valid @NotNull UserOrganizationScopeSaveReqVO request);
    Long revise(Long id, @Valid @NotNull UserOrganizationScopeSaveReqVO request);
    void disable(Long id, @Valid @NotNull UserOrganizationScopeDisableReqVO request);
}
