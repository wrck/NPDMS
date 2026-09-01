package cn.iocoder.yudao.module.pms.project.api.systemqualification;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationFact;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectSystemQualificationFactApiImpl implements ProjectSystemQualificationFactApi {

    private static final String ACTIVE = "ACTIVE";
    private static final String S4 = "S4";

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper treeVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSystemQualificationFact lockCurrentForSystem(ProjectSystemQualificationLockQuery query) {
        validateQuery(query);
        Long tenantId = trustedTenantId();

        ProjectMasterDO observed = requireProject(projectMapper.selectById(query.projectId()), tenantId);
        long rootId = observed.getRootId() == null ? observed.getId() : observed.getRootId();

        ProjectMasterDO lockedRoot = requireProject(projectMapper.selectByIdForUpdate(rootId), tenantId);
        requireRootIdentity(lockedRoot, rootId);
        ProjectMasterDO lockedProject = Objects.equals(rootId, query.projectId())
                ? lockedRoot : requireProject(projectMapper.selectByIdForUpdate(query.projectId()), tenantId);
        if (!Objects.equals(rootId, lockedProject.getRootId() == null
                ? lockedProject.getId() : lockedProject.getRootId())) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        requireCurrentQualification(lockedProject);

        ProjectTreeVersionDO treeVersion = treeVersionMapper.selectLatestActiveForUpdate(rootId);
        requireCurrentTreeVersion(treeVersion, tenantId, rootId);
        return new ProjectSystemQualificationFact(lockedProject.getId(), lockedProject.getManagerId(),
                lockedProject.getLifecycleStatus(), lockedProject.getCurrentStage(), lockedProject.getVersion(),
                lockedProject.getVersion().longValue(), treeVersion.getTreeVersion());
    }

    private void validateQuery(ProjectSystemQualificationLockQuery query) {
        if (query == null || query.projectId() == null || query.projectId() <= 0
                || !ACTIVE.equals(query.requiredLifecycleStatus()) || !S4.equals(query.requiredCurrentStage())) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private Long trustedTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId < 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return tenantId;
    }

    private ProjectMasterDO requireProject(ProjectMasterDO project, Long tenantId) {
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)
                || project.getId() == null || project.getId() <= 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        return project;
    }

    private void requireRootIdentity(ProjectMasterDO root, long rootId) {
        if (!Objects.equals(root.getId(), rootId)
                || root.getRootId() != null && !Objects.equals(root.getRootId(), rootId)) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
    }

    private void requireCurrentQualification(ProjectMasterDO project) {
        if (!ACTIVE.equals(project.getLifecycleStatus()) || !S4.equals(project.getCurrentStage())
                || project.getManagerId() == null || project.getManagerId() <= 0
                || project.getVersion() == null || project.getVersion() < 0) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
    }

    private void requireCurrentTreeVersion(ProjectTreeVersionDO treeVersion, Long tenantId, long rootId) {
        if (treeVersion == null || !Objects.equals(treeVersion.getTenantId(), tenantId)
                || !Objects.equals(treeVersion.getRootProjectId(), rootId)
                || !ACTIVE.equals(treeVersion.getStatus()) || treeVersion.getTreeVersion() == null
                || treeVersion.getTreeVersion() < 0) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
    }

}
