package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.Context;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** One short write admission check, shared by embedded and standalone Owner commands. */
@Service
@RequiredArgsConstructor
public class ProjectBusinessExecutionService implements ProjectBusinessExecutionApi {
    private final ProjectMasterMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper nodes;
    private final ProjectNodeExecutionApi executions;
    private final TaskBusinessAccess taskAccess;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final BusinessViewQueryApi views;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForWrite(WriteRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || request == null || request.projectId() == null || request.projectId() <= 0
                || request.ownerContext() == null || request.objectType() == null)
            throw exception(PROJECT_TASK_QUERY_INVALID);
        var selection = request.selection();
        if (selection != null && ((selection.task() == null) == (selection.stage() == null)
                || !Objects.equals(request.projectId(), selection.task() != null
                ? selection.task().projectId() : selection.stage().projectId())))
            throw exception(PROJECT_TASK_QUERY_INVALID);
        // The same project-first lock order as plan changes, rework and completion.
        var project = projects.selectByIdForUpdate(request.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        if (project.getActivePlanVersionId() == null && project.getLifecycleTemplateId() == null) {
            if (selection != null) throw exception(PROJECT_TASK_QUERY_INVALID);
            return; // An unmanaged project remains governed by the Owner's ordinary business permissions.
        }
        var scope = new ProjectPlanScopeQuery(tenantId, project.getId());
        var plan = plans.selectEffective(scope);
        if (!"ACTIVE".equals(project.getLifecycleStatus()) || plan == null
                || !Objects.equals(project.getActivePlanVersionId(), plan.getId())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var candidates = new ArrayList<ProjectNodeExecutionDO>();
        for (var node : nodes.selectCurrent(scope)) {
            if (!Objects.equals(node.getPlanVersionId(), plan.getId()) || !"ACTIVE".equals(node.getStatus())) continue;
            if (selection != null && !(selection.task() != null
                    ? "TASK".equals(node.getNodeKind()) && Objects.equals(node.getId(), selection.task().executionId())
                    : "STAGE".equals(node.getNodeKind()) && Objects.equals(node.getId(), selection.stage().executionId()))) continue;
            var binding = binding(snapshot, node);
            if (!matches(binding, request)) continue;
            // Standalone menus may resolve only a single actually writable node, never the first matching record.
            if (selection == null && !writable(project.getId(), node)) continue;
            candidates.add(node);
        }
        if (candidates.size() != 1) throw exception(PROJECT_BUSINESS_EXECUTION_REQUIRED);
        var node = candidates.getFirst();
        requireWritableView(binding(snapshot, node));
        if ("TASK".equals(node.getNodeKind())) {
            var task = taskAccess.read(node.getNodeInstanceId(), tenantId, actorId);
            if (!taskAccess.writable(task, project, new Context(tenantId, actorId, project.getId(), task.getId(), null)))
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            var expected = selection == null ? executions.inspect(taskQuery(project.getId(), node)) : selection.task();
            executions.lockAndRevalidate(expected);
        } else {
            var allowed = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, project.getId(), ProjectScopeApi.ACTION_EDIT));
            if (!permissions.hasAnyPermissions(actorId, "pms:project-task:execute") || allowed == null
                    || allowed.fullProjectIds() == null || !allowed.fullProjectIds().contains(project.getId()))
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            var expected = selection == null ? executions.inspectStage(stageQuery(project.getId(), node)) : selection.stage();
            // Mark real stage work in this same Owner transaction; failure rolls it back with the Owner record.
            executions.beginStageHandling(expected, actorId);
        }
    }

    private BindingContract binding(TemplateExecutionSnapshot snapshot, ProjectNodeExecutionDO node) {
        if ("TASK".equals(node.getNodeKind())) {
            var definitions = snapshot.getTasks().stream().filter(d -> Objects.equals(d.getNodeKey(), node.getNodeKey())).toList();
            if (definitions.size() != 1) throw exception(PROJECT_TASK_QUERY_INVALID);
            return definitions.getFirst().getBinding();
        }
        if ("STAGE".equals(node.getNodeKind())) {
            var definitions = snapshot.getStages().stream().filter(d -> Objects.equals(d.getNodeKey(), node.getNodeKey())).toList();
            if (definitions.size() != 1) throw exception(PROJECT_TASK_QUERY_INVALID);
            return definitions.getFirst().getBinding();
        }
        return null;
    }

    private boolean matches(BindingContract binding, WriteRequest request) {
        return binding != null && ("BUSINESS_COMPONENT".equals(binding.getType()) || "BUSINESS_OBJECT".equals(binding.getType()))
                && Objects.equals(request.ownerContext(), binding.getTargetContextCode())
                && Objects.equals(request.objectType(), binding.getTargetObjectType());
    }

    private boolean writable(Long projectId, ProjectNodeExecutionDO node) {
        if ("TASK".equals(node.getNodeKind())) return executions.inspect(taskQuery(projectId, node)).writable();
        return executions.inspectStage(stageQuery(projectId, node)).writable();
    }

    private ProjectTaskExecutionQuery taskQuery(Long projectId, ProjectNodeExecutionDO node) {
        return new ProjectTaskExecutionQuery(projectId, node.getNodeInstanceId(), node.getContractId());
    }

    private ProjectStageExecutionQuery stageQuery(Long projectId, ProjectNodeExecutionDO node) {
        return new ProjectStageExecutionQuery(projectId, node.getNodeInstanceId(), node.getContractId());
    }

    private void requireWritableView(BindingContract binding) {
        String strategy = binding.getParameters() == null ? "" : binding.getParameters().path("instanceResolutionStrategy").asText();
        if (!Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION").contains(strategy) || binding.getBusinessViewSnapshot() == null)
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        var frozen = JsonUtils.convertObject(binding.getBusinessViewSnapshot(), BusinessViewRevision.class);
        if (frozen == null || frozen.id() == null) throw exception(PROJECT_TASK_COMMAND_INVALID);
        var current = views.getRevision(new BusinessViewQueryApi.Query(frozen.id(), BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
        if (current == null || !"PUBLISHED".equals(current.status()) || !Objects.equals(frozen.id(), current.id())
                || !Objects.equals(binding.getTargetContextCode(), current.ownerContext())
                || !Objects.equals(binding.getTargetObjectType(), current.entityType())
                || !Objects.equals(binding.getComponentKey(), current.componentKey())
                || !Objects.equals(frozen.componentVersion(), current.componentVersion())) throw exception(PROJECT_TASK_COMMAND_INVALID);
    }
}
