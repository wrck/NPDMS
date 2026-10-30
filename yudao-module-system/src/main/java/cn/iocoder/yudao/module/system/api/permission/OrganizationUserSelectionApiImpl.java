package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.company.CompanyDO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.company.CompanyMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.OrganizationUserSelectionMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.OrganizationUserCandidatePageQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;

/** 复制既有组织候选读取后增强；旧 OrganizationScopeApi 保持原搜索契约。 */
@Service
public class OrganizationUserSelectionApiImpl implements OrganizationUserSelectionApi {

    @Resource
    private OrganizationUserSelectionMapper selectionMapper;
    @Resource
    private CompanyMapper companyMapper;
    @Resource
    private DeptMapper deptMapper;

    @Override
    public PageResult<OrganizationUserCandidateRespDTO> page(
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
        long total = selectionMapper.selectCount(query);
        if (total == 0) {
            return PageResult.empty();
        }
        return new PageResult<>(selectionMapper.selectPage(query), total);
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
