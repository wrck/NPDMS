package cn.iocoder.yudao.module.pms.project.api.deadline;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectEndDateUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class ProjectEndDateApiImpl implements ProjectEndDateApi {
    private final ProjectScopeApi scopeApi;
    private final ProjectMasterMapper projectMapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void updateFromSurvey(ProjectEndDateCommand command) {
        var project = lockProject(command);
        if (Objects.equals(project.getProjectEndDate(), command.endDate())) return;
        if (projectMapper.updateEndDateIfMatch(new ProjectEndDateUpdate(command.tenantId(), command.projectId(),
                command.expectedProjectVersion(), command.endDate(), String.valueOf(command.actorUserId()))) != 1) throw exception(PROJECT_END_DATE_CONFLICT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void validatePlanningEndDate(ProjectEndDateCommand command) {
        // Duration services already hold their participant/project lock. Do not acquire a tree-root
        // lock after it: survey writes use the opposite (tree root -> project) order.
        var scope = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(command.tenantId(), command.actorUserId(), command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) throw exception(PROJECT_END_DATE_NOT_ALLOWED);
        var project = projectMapper.selectEndDateForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectEndDateRowQuery(command.tenantId(), command.projectId()));
        if (project == null || !Objects.equals(project.getTenantId(), command.tenantId()) || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_END_DATE_NOT_ALLOWED);
        if (command.endDate() == null || command.expectedProjectVersion() == null || !Objects.equals(project.getVersion(), command.expectedProjectVersion())) throw exception(PROJECT_END_DATE_CONFLICT);
        // Projects without a survey requirement retain their existing duration-entry workflow.
        if (project.getProjectEndDate() != null && !project.getProjectEndDate().equals(command.endDate())) {
            throw exception(PROJECT_END_DATE_CONFLICT);
        }
    }

    private cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO lockProject(ProjectEndDateCommand command) {
        if (command.endDate() == null || command.expectedProjectVersion() == null) throw exception(PROJECT_END_DATE_CONFLICT);
        var scope = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(command.tenantId(), command.actorUserId(), command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) throw exception(PROJECT_END_DATE_NOT_ALLOWED);
        scope = scopeApi.lockAndRevalidate(new cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery(
                command.tenantId(), command.actorUserId(), command.projectId(), ProjectScopeApi.ACTION_MANAGE, scope.treeVersion()));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) throw exception(PROJECT_END_DATE_NOT_ALLOWED);
        var project = projectMapper.selectEndDateForUpdate(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectEndDateRowQuery(command.tenantId(), command.projectId()));
        if (project == null || !Objects.equals(project.getTenantId(), command.tenantId()) || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_END_DATE_NOT_ALLOWED);
        if (!Objects.equals(project.getVersion(), command.expectedProjectVersion())) throw exception(PROJECT_END_DATE_CONFLICT);
        return project;
    }
}
