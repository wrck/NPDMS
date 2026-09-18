package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi.WriteRequest;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectOperationControlScope;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectOperationEntryPolicy;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Ordinary Owner commands lock their project scope, not an inferred active task or presentation. */
@Component
@RequiredArgsConstructor
public class ProjectIndependentOperationAdmission {
    private final ProjectOperationEntryPolicy policies;
    private final ProjectScopeApi scopes;
    private final ObjectProvider<ProjectOperationContextResolver> contexts;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean admit(WriteRequest request) {
        var scope = policies.scope(request);
        if (scope == null) return false;
        // ALL_ENTRIES must enter its verified command boundary even when a caller supplies node IDs.
        if (scope != ProjectOperationControlScope.PROJECT_ENTRY_ONLY) throw exception(PROJECT_BUSINESS_EXECUTION_REQUIRED);
        if (request.selection() != null) {
            var selected = request.selection();
            if ((selected.task() == null) == (selected.stage() == null)
                    || !Objects.equals(request.projectId(), selected.task() != null
                    ? selected.task().projectId() : selected.stage().projectId())) throw exception(PROJECT_TASK_QUERY_INVALID);
            var context = contexts.getObject().resolve(request.projectId(), selected.task() != null ? "TASK" : "STAGE",
                    selected.task() != null ? selected.task().taskId() : selected.stage().stageId(), selected);
            if (context == null || context.reason() != null) throw exception(PROJECT_BUSINESS_EXECUTION_REQUIRED);
            // Supplying an execution selection is not proof that the frozen PRE/POST command ran.
            if (context.binding() != null && context.binding().getOperationContract() != null)
                throw exception(PROJECT_BUSINESS_EXECUTION_REQUIRED);
            return false; // Positively identified legacy binding keeps the original admission checks.
        }
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0 || request.projectId() == null || request.projectId() <= 0
                || request.ownerProof() != null) throw exception(PROJECT_TASK_QUERY_INVALID);
        var observed = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, request.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (observed == null || observed.treeVersion() == null || observed.fullProjectIds() == null
                || !observed.fullProjectIds().contains(request.projectId())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, actor, request.projectId(),
                ProjectScopeApi.ACTION_MANAGE, observed.treeVersion()));
        if (locked == null || locked.fullProjectIds() == null || !locked.fullProjectIds().contains(request.projectId()))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        return true;
    }
}
