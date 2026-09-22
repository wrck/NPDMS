package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.satisfaction.SatisfactionQuestionnaireTemplateApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_QUERY_INVALID;

@Service
@RequiredArgsConstructor
public class ProjectManualSatisfactionApiImpl implements ProjectManualSatisfactionApi {
    private final ProjectMasterMapper projects;
    private final ProjectWorkBindingFactMapper facts;
    private final ProjectWorkBindingFactApi bindings;
    private final ProjectScopeApi scopes;
    private final SatisfactionQuestionnaireTemplateApi templates;

    @Override
    public Options options(Long projectId, Long actorId) {
        requireScope(projectId, actorId);
        var tasks = facts.selectManualSatisfactionOptions(new ProjectSatisfactionTaskProjectLockQuery(
                TenantContextHolder.getRequiredTenantId(), projectId));
        return new Options(tasks.stream().anyMatch(task -> task.templateId() != null), tasks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProjectSatisfactionTaskFact freeze(Selection selection) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        requireScope(selection.projectId(), selection.actorId());
        var project = projects.selectByIdForUpdate(selection.projectId());
        if (project == null || !Objects.equals(tenant, project.getTenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_TASK_QUERY_INVALID);
        var current = facts.selectProjectSatisfactionTaskForUpdate(
                new ProjectSatisfactionTaskProjectLockQuery(tenant, selection.projectId()));
        if (!current.isEmpty()) {
            return bindings.lockCurrentSatisfactionTaskByProject(new ProjectSatisfactionTaskProjectQuery(selection.projectId()));
        }
        if (selection.projectTaskId() == null || selection.templateId() == null || selection.revisionId() == null) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        var task = facts.selectProjectTaskForUpdate(new ProjectWorkBindingFactLockQuery(
                tenant, selection.projectId(), selection.projectTaskId()));
        if (task == null || task.getActualEndTime() != null) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        var template = templates.inspectPublished(selection.templateId(), selection.revisionId());
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, selection.actorId(),
                selection.projectId(), ProjectScopeApi.ACTION_EDIT));
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, selection.actorId(),
                selection.projectId(), ProjectScopeApi.ACTION_EDIT, scope.treeVersion()));
        if (locked == null || !Objects.equals(scope.treeVersion(), locked.treeVersion())
                || !locked.fullProjectIds().contains(selection.projectId())) throw exception(PROJECT_TASK_QUERY_INVALID);
        if (facts.freezeManualSatisfaction(new ProjectManualSatisfactionFreeze(tenant, selection.projectId(),
                task.getId(), task.getVersion(), template.templateId(), template.templateRevisionId(),
                template.templateVersion(), template.ruleVersion(), template.threshold(), String.valueOf(selection.actorId()))) != 1) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
        return bindings.lockCurrentSatisfactionTaskByProject(new ProjectSatisfactionTaskProjectQuery(selection.projectId()));
    }

    private void requireScope(Long projectId, Long actorId) {
        if (projectId == null || projectId <= 0 || actorId == null || actorId <= 0) throw exception(PROJECT_TASK_QUERY_INVALID);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(TenantContextHolder.getRequiredTenantId(),
                actorId, projectId, ProjectScopeApi.ACTION_EDIT));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_TASK_QUERY_INVALID);
        }
    }
}
