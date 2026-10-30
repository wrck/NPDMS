package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope.*;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.UserOrganizationScopePageQuery;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
@Validated
@RequiredArgsConstructor
public class UserOrganizationScopeServiceImpl implements UserOrganizationScopeService {
    private final UserCompanyDepartmentScopeMapper scopeMapper;
    private final AdminUserMapper userMapper;
    private final CompanyMapper companyMapper;
    private final DeptMapper deptMapper;

    @Override
    public PageResult<UserCompanyDepartmentScopeDO> getPage(UserOrganizationScopePageReqVO request) {
        requireUser(request.getUserId());
        return scopeMapper.selectMaintenancePage(BeanUtils.toBean(request, UserOrganizationScopePageQuery.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = "SYSTEM 用户", subType = "新增公司部门范围", bizNo = "{{#request.userId}}",
            success = "新增公司部门范围【{{#scope.id}}】，公司【{{#scope.companyCode}}】，部门【{{#scope.departmentCode}}】，角色【{{#scope.scopeRole}}】")
    public Long create(UserOrganizationScopeSaveReqVO request) {
        UserCompanyDepartmentScopeDO scope = buildScope(request);
        scopeMapper.insert(scope);
        LogRecordContext.putVariable("scope", scope);
        return scope.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = "SYSTEM 用户", subType = "变更公司部门范围", bizNo = "{{#request.userId}}",
            success = "停用原公司部门范围【{{#id}}】，新增修订范围【{{#scope.id}}】，公司【{{#scope.companyCode}}】，部门【{{#scope.departmentCode}}】，角色【{{#scope.scopeRole}}】")
    public Long revise(Long id, UserOrganizationScopeSaveReqVO request) {
        UserCompanyDepartmentScopeDO existing = requireScope(id, request.getUserId());
        requireVersion(existing, request.getExpectedVersion());
        UserCompanyDepartmentScopeDO scope = buildScope(request);
        // 原行只停用，不覆盖公司、部门、角色、生效区间等来源事实。
        disableExisting(existing);
        scopeMapper.insert(scope);
        LogRecordContext.putVariable("scope", scope);
        return scope.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = "SYSTEM 用户", subType = "停用公司部门范围", bizNo = "{{#request.userId}}",
            success = "停用公司部门范围【{{#id}}】，保留原范围及历史")
    public void disable(Long id, UserOrganizationScopeDisableReqVO request) {
        // 停用无须公司、部门或用户仍处于启用状态，以免失效主数据阻止撤权。
        UserCompanyDepartmentScopeDO existing = requireScope(id, request.getUserId());
        requireVersion(existing, request.getExpectedVersion());
        disableExisting(existing);
    }

    private UserCompanyDepartmentScopeDO requireScope(Long id, Long userId) {
        requireUser(userId); // 沿用 SYSTEM 用户数据权限，不能凭 scope ID 越过用户可见范围。
        UserCompanyDepartmentScopeDO existing = scopeMapper.selectById(id);
        if (existing == null || !Objects.equals(existing.getUserId(), userId)) {
            throw exception(USER_ORGANIZATION_SCOPE_NOT_FOUND);
        }
        return existing;
    }

    private void requireVersion(UserCompanyDepartmentScopeDO scope, Integer version) {
        if (version == null || !Objects.equals(scope.getVersion(), version)
                || !CommonStatusEnum.isEnable(scope.getStatus())) {
            throw exception(USER_ORGANIZATION_SCOPE_VERSION_CONFLICT);
        }
    }

    private void disableExisting(UserCompanyDepartmentScopeDO existing) {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        existing.setUpdater(actorId == null ? "" : actorId.toString());
        if (scopeMapper.disableByVersion(existing) != 1) {
            throw exception(USER_ORGANIZATION_SCOPE_VERSION_CONFLICT);
        }
    }

    private AdminUserDO requireUser(Long userId) {
        AdminUserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    private UserCompanyDepartmentScopeDO buildScope(UserOrganizationScopeSaveReqVO request) {
        AdminUserDO user = requireUser(request.getUserId());
        if (!CommonStatusEnum.isEnable(user.getStatus())) {
            throw exception(USER_IS_DISABLE, user.getNickname());
        }
        if (request.getEffectiveTo() != null && !request.getEffectiveTo().isAfter(request.getEffectiveFrom())) {
            throw exception(USER_ORGANIZATION_SCOPE_INTERVAL_INVALID);
        }
        CompanyDO company = companyMapper.selectById(request.getCompanyId());
        if (company == null || !CommonStatusEnum.isEnable(company.getStatus())) {
            throw exception(ORGANIZATION_SCOPE_INVALID);
        }
        DeptDO department = request.getDepartmentId() == null ? null : deptMapper.selectById(request.getDepartmentId());
        if (request.getDepartmentId() != null && (department == null
                || !CommonStatusEnum.isEnable(department.getStatus())
                || department.getCode() == null || department.getCode().isBlank())) {
            throw exception(ORGANIZATION_SCOPE_INVALID);
        }
        UserCompanyDepartmentScopeDO scope = new UserCompanyDepartmentScopeDO()
                .setUserId(request.getUserId()).setCompanyId(company.getId())
                .setCompanyCode(company.getCode()).setCompanyName(company.getName())
                .setScopeRole(request.getScopeRole().trim()).setIsPrimary(request.getIsPrimary())
                .setEffectiveFrom(request.getEffectiveFrom()).setEffectiveTo(request.getEffectiveTo())
                .setStatus(CommonStatusEnum.ENABLE.getStatus()).setVersion(0);
        if (department != null) {
            scope.setDepartmentId(department.getId()).setDepartmentCode(department.getCode())
                    .setDepartmentName(department.getName());
        }
        return scope;
    }
}
