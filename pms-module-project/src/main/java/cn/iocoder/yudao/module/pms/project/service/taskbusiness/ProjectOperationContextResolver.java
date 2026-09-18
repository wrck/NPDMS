package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration;
import tools.jackson.databind.JsonNode;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectOperationCapabilities.Node;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

/** Authorizes the node before exposing frozen bindings. All reads are side-effect free. */
@Component
@RequiredArgsConstructor
public class ProjectOperationContextResolver {
    private final TaskBusinessAccess taskAccess;
    private final ProjectManualCreationService projectAccess;
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper nodes;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectNodeExecutionApi executions;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;

    public record Context(Long tenantId, Long actorId, ProjectMasterDO project, Node node,
            ProjectNodeExecutionDO round, TemplateExecutionSnapshot.BindingContract binding,
            ProjectBusinessExecutionSelection selection, boolean permitted, String reason,
            TemplateExecutionConfiguration.Presentation presentation) {
        public Context(Long tenantId, Long actorId, ProjectMasterDO project, Node node,
                ProjectNodeExecutionDO round, TemplateExecutionSnapshot.BindingContract binding,
                ProjectBusinessExecutionSelection selection, boolean permitted, String reason) {
            this(tenantId, actorId, project, node, round, binding, selection, permitted, reason, null);
        }
    }
    private record FrozenNode(TemplateExecutionSnapshot.BindingContract binding, JsonNode execution) { }

    public Context resolve(Long projectId, String kind, Long nodeId, ProjectBusinessExecutionSelection expected) {
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0 || projectId == null || projectId <= 0 || nodeId == null || nodeId <= 0
                || !Set.of("TASK", "STAGE").contains(String.valueOf(kind))) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        Node summary;
        ProjectTaskInstanceDO task = null;
        if ("TASK".equals(kind)) {
            task = taskAccess.read(nodeId, tenant, actor);
            if (!Objects.equals(projectId, task.getProjectId())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            summary = new Node(projectId, kind, nodeId, task.getCode(), task.getName(), task.getStatus());
        } else {
            if (!permissions.hasAnyPermissions(actor, "pms:project:query")) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            projectAccess.getProject(projectId, new ProjectManualCreationService.ProjectAccessActor(tenant, actor));
            var stages = graph.selectStages(new ProjectRuntimeGraphQuery(tenant, projectId)).stream()
                    .filter(stage -> nodeId.equals(stage.getId()) && tenant.equals(stage.getTenantId())
                            && projectId.equals(stage.getProjectId())).toList();
            if (stages.size() != 1) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            var stage = stages.getFirst();
            summary = new Node(projectId, kind, nodeId, stage.getCode(), stage.getName(), stage.getStatus());
        }
        var project = projects.selectById(projectId);
        if (project == null || !tenant.equals(project.getTenantId())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var scope = new ProjectPlanScopeQuery(tenant, projectId);
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(project.getActivePlanVersionId(), plan.getId())
                || !tenant.equals(plan.getTenantId()) || !projectId.equals(plan.getProjectId()))
            return unavailable(tenant, actor, project, summary, "EFFECTIVE_PLAN_UNAVAILABLE");
        var matches = nodes.selectCurrent(scope).stream().filter(round -> kind.equals(round.getNodeKind())
                && nodeId.equals(round.getNodeInstanceId())).toList();
        if (matches.size() != 1) return unavailable(tenant, actor, project, summary, "EXECUTION_CONTEXT_NOT_UNIQUE");
        var round = matches.getFirst();
        if (!tenant.equals(round.getTenantId()) || !projectId.equals(round.getProjectId())
                || !plan.getId().equals(round.getPlanVersionId()) || round.getContractId() == null)
            return unavailable(tenant, actor, project, summary, "EXECUTION_CONTEXT_STALE");
        try {
            var frozen = TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot());
            var bindings = "TASK".equals(kind) ? frozen.getTasks().stream()
                    .filter(node -> Objects.equals(node.getNodeKey(), round.getNodeKey()) && summary.code().equals(node.getCode()))
                    .map(node -> new FrozenNode(node.getBinding(), node.getExecution())).toList()
                    : frozen.getStages().stream()
                    .filter(node -> Objects.equals(node.getNodeKey(), round.getNodeKey()) && summary.code().equals(node.getCode()))
                    .map(node -> new FrozenNode(node.getBinding(), node.getExecution())).toList();
            if (bindings.size() != 1) return unavailable(tenant, actor, project, summary, "FROZEN_NODE_NOT_UNIQUE");
            var matched = bindings.getFirst();
            var presentation = matched.execution() == null ? null
                    : TemplateExecutionConfiguration.read(matched.execution()).presentation();
            ProjectBusinessExecutionSelection selection;
            boolean permitted;
            if ("TASK".equals(kind)) {
                var current = executions.inspect(new ProjectTaskExecutionQuery(projectId, nodeId, round.getContractId()));
                if (!Objects.equals(current.executionId(), round.getId()) || !Objects.equals(current.planVersionId(), plan.getId()))
                    return unavailable(tenant, actor, project, summary, "EXECUTION_CONTEXT_STALE");
                selection = new ProjectBusinessExecutionSelection(current, null);
                permitted = current.writable() && taskAccess.executable(task, project,
                        new TaskBusinessObjectProvider.Context(tenant, actor, projectId, nodeId, null));
            } else {
                var current = executions.inspectStage(new ProjectStageExecutionQuery(projectId, nodeId, round.getContractId()));
                if (!Objects.equals(current.executionId(), round.getId()) || !Objects.equals(current.planVersionId(), plan.getId()))
                    return unavailable(tenant, actor, project, summary, "EXECUTION_CONTEXT_STALE");
                selection = new ProjectBusinessExecutionSelection(null, current);
                var edit = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, projectId, ProjectScopeApi.ACTION_EDIT));
                permitted = current.writable() && permissions.hasAnyPermissions(actor, "pms:project-task:execute")
                        && edit != null && edit.fullProjectIds() != null && edit.fullProjectIds().contains(projectId);
            }
            if (expected != null && !expected.equals(selection))
                return new Context(tenant, actor, project, summary, round, matched.binding(), selection, false, "EXECUTION_VERSION_CONFLICT", presentation);
            return new Context(tenant, actor, project, summary, round, matched.binding(), selection, permitted, null, presentation);
        } catch (RuntimeException unavailable) {
            return unavailable(tenant, actor, project, summary, "EXECUTION_CONTEXT_UNAVAILABLE");
        }
    }

    private Context unavailable(Long tenant, Long actor, ProjectMasterDO project, Node node, String reason) {
        return new Context(tenant, actor, project, node, null, null, null, false, reason);
    }
}
