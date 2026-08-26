package cn.iocoder.yudao.module.pms.project.api.organization;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectOrganizationFactApiImpl implements ProjectOrganizationFactApi {

    private final ProjectMasterMapper projectMapper;

    @Override
    public ProjectOrganizationFact inspect(ProjectOrganizationFactQuery query) {
        validate(query == null ? null : query.projectId(), null, false);
        return fact(requireProject(projectMapper.selectById(query.projectId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectOrganizationFact lockAndRevalidate(ProjectOrganizationFactRevalidationQuery query) {
        validate(query == null ? null : query.projectId(),
                query == null ? null : query.expectedProjectVersion(), true);
        ProjectMasterDO project = requireProject(projectMapper.selectByIdForUpdate(query.projectId()));
        if (!Objects.equals(project.getVersion(), query.expectedProjectVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        return fact(project);
    }

    private void validate(Long projectId, Integer expectedVersion, boolean revalidate) {
        if (projectId == null || projectId <= 0
                || revalidate && (expectedVersion == null || expectedVersion < 0)) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private ProjectMasterDO requireProject(ProjectMasterDO project) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0 || project == null
                || !Objects.equals(project.getTenantId(), tenantId)
                || project.getVersion() == null || project.getVersion() < 0
                || project.getCompanyId() == null || project.getCompanyId() <= 0
                || project.getDepartmentId() == null || project.getDepartmentId() <= 0
                || project.getDepartmentCode() == null || project.getDepartmentCode().isBlank()) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return project;
    }

    private ProjectOrganizationFact fact(ProjectMasterDO project) {
        return new ProjectOrganizationFact(project.getId(), project.getVersion(), project.getCompanyId(),
                project.getDepartmentId(), project.getDepartmentCode().trim());
    }
}
