package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.UserCompanyDepartmentScopeRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserCompanyDepartmentScopeDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserCompanyDepartmentScopeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ActiveUserScopeListQuery;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.OrganizationUserCandidatePageQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;

@Service
public class OrganizationScopeApiImpl implements OrganizationScopeApi {

    @Resource
    private UserCompanyDepartmentScopeMapper scopeMapper;
    @Resource
    private CompanyMapper companyMapper;
    @Resource
    private DeptMapper deptMapper;

    @Override
    public List<UserCompanyDepartmentScopeRespDTO> getActiveScopes(Long userId) {
        List<UserCompanyDepartmentScopeDO> scopes = scopeMapper.selectActiveListByUserId(
                ActiveUserScopeListQuery.builder().userId(userId).currentTime(LocalDateTime.now())
                        .enabledStatus(CommonStatusEnum.ENABLE.getStatus()).build());
        return BeanUtils.toBean(scopes, UserCompanyDepartmentScopeRespDTO.class);
    }

    @Override
    public boolean hasScope(Long userId, Long companyId, Long departmentId) {
        return getActiveScopes(userId).stream().anyMatch(scope ->
                Objects.equals(scope.getCompanyId(), companyId)
                        && Objects.equals(scope.getDepartmentId(), departmentId));
    }

    @Override
    public PageResult<OrganizationUserCandidateRespDTO> pageActiveUsers(
            OrganizationUserCandidatePageReqDTO request) {
        validateCandidateRequest(request);
        int enabledStatus = CommonStatusEnum.ENABLE.getStatus();
        CompanyDO company = companyMapper.selectById(request.getCompanyId());
        DeptDO department = deptMapper.selectById(request.getDepartmentId());
        if (company == null || !Objects.equals(company.getStatus(), enabledStatus)
                || department == null || !Objects.equals(department.getStatus(), enabledStatus)
                || !Objects.equals(department.getCode(), request.getDepartmentCode())) {
            throw exception(ORGANIZATION_SCOPE_INVALID);
        }
        OrganizationUserCandidatePageQuery query = OrganizationUserCandidatePageQuery.builder()
                .companyId(request.getCompanyId())
                .departmentId(request.getDepartmentId())
                .departmentCode(request.getDepartmentCode())
                .keyword(trimToNull(request.getKeyword()))
                .enabledStatus(enabledStatus)
                .currentTime(LocalDateTime.now())
                .offset((request.getPageNo() - 1) * request.getPageSize())
                .limit(request.getPageSize())
                .build();
        long total = scopeMapper.selectActiveUserCandidateCount(query);
        if (total == 0) {
            return PageResult.empty();
        }
        return new PageResult<>(scopeMapper.selectActiveUserCandidatePage(query), total);
    }

    private static void validateCandidateRequest(OrganizationUserCandidatePageReqDTO request) {
        if (request == null || request.getCompanyId() == null || request.getDepartmentId() == null
                || trimToNull(request.getDepartmentCode()) == null || request.getPageNo() == null
                || request.getPageNo() < 1 || request.getPageSize() == null
                || request.getPageSize() < 1 || request.getPageSize() > 100
                || request.getKeyword() != null && request.getKeyword().length() > 64) {
            throw exception(ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
