package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFact;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class ProjectOfficeFactApiImpl implements ProjectOfficeFactApi {

    private static final String ACTIVE = "ACTIVE";

    private final ProjectMasterMapper projectMapper;
    private final DeptApi deptApi;

    @Override
    public ProjectOfficeFact resolve(ProjectOfficeFactQuery query) {
        validate(query);
        return resolveProject(query, projectMapper.selectById(query.projectId()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public ProjectOfficeFact lockAndRevalidate(ProjectOfficeFactQuery query) {
        validate(query);
        return resolveProject(query, projectMapper.selectByIdForUpdate(query.projectId()));
    }

    private ProjectOfficeFact resolveProject(ProjectOfficeFactQuery query, ProjectMasterDO project) {
        if (project == null) {
            return outcome(ProjectFactOutcome.NOT_FOUND, query.projectId(), null);
        }
        requireTenant(project.getTenantId(), query.tenantId());
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) {
            return outcome(ProjectFactOutcome.VERSION_CONFLICT, project.getId(), project.getVersion());
        }
        if (!ACTIVE.equals(project.getLifecycleStatus())) {
            return outcome(ProjectFactOutcome.INACTIVE, project.getId(), project.getVersion());
        }
        if (project.getDepartmentId() == null || project.getDepartmentId() <= 0
                || project.getDepartmentCode() == null || project.getDepartmentCode().isBlank()) {
            return outcome(ProjectFactOutcome.NOT_FOUND, project.getId(), project.getVersion());
        }
        DeptRespDTO department = deptApi.getDept(project.getDepartmentId());
        if (department == null) {
            return outcome(ProjectFactOutcome.NOT_FOUND, project.getId(), project.getVersion());
        }
        if (!Objects.equals(department.getId(), project.getDepartmentId())
                || department.getCode() == null
                || !project.getDepartmentCode().trim().equals(department.getCode().trim())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        if (!Objects.equals(department.getStatus(), CommonStatusEnum.ENABLE.getStatus())) {
            return outcome(ProjectFactOutcome.INACTIVE, project.getId(), project.getVersion());
        }
        if (department.getName() == null || department.getName().isBlank()
                || department.getVersion() == null || department.getVersion() < 0) {
            return outcome(ProjectFactOutcome.NOT_FOUND, project.getId(), project.getVersion());
        }
        return new ProjectOfficeFact(ProjectFactOutcome.FOUND, project.getId(), project.getVersion(),
                department.getId(), department.getCode().trim(), department.getName().trim(),
                department.getVersion());
    }

    private void validate(ProjectOfficeFactQuery query) {
        Long trustedTenantId = TenantContextHolder.getTenantId();
        if (query == null || trustedTenantId == null || trustedTenantId < 0
                || !Objects.equals(query.tenantId(), trustedTenantId)
                || query.projectId() == null || query.projectId() <= 0
                || query.expectedProjectVersion() == null || query.expectedProjectVersion() < 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private void requireTenant(Long actualTenantId, Long expectedTenantId) {
        if (!Objects.equals(actualTenantId, expectedTenantId)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private ProjectOfficeFact outcome(ProjectFactOutcome outcome, Long projectId, Integer version) {
        return new ProjectOfficeFact(outcome, projectId, version, null, null, null, null);
    }
}
