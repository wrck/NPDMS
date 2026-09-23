package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.StagePlanCompletionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.StagePlanCompletionQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Read-only Owner facts; original business commands retain their authorization and state transitions. */
@Service
@RequiredArgsConstructor
public class StagePlanApprovalCompletionProvider implements TaskBusinessObjectProvider, StageBusinessViewProvider {
    public static final String FACT = "CONSTRUCTION_PLAN_APPROVED";
    private final StagePlanCompletionMapper mapper;
    private final ProjectScopeApi scope;
    private final PermissionApi permissions;
    private final ProjectNodeExecutionApi executions;

    private final cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper plans;

    @Override public String ownerContext() { return "PLN"; }
    @Override public String objectType() { return "STAGE_PLAN"; }
    @Override public Set<String> completionFactCodes() { return Set.of(FACT); }
    @Override public Map<String, String> completionFactLabels() { return Map.of(FACT, "当前施工计划已审批生效且工期基线一致"); }
    @Override public boolean supportsStageCompletionFacts() { return true; }

    @Override public Set<String> inspectContext(TaskBusinessObjectProvider.Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0) throw exception(FORBIDDEN);
        authorize(context.tenantId(), context.actorId(), context.projectId());
        return Set.of("QUERY");
    }

    @Override public StageBusinessViewProvider.Result inspectStage(StageBusinessViewProvider.Context context) {
        if (context == null || context.stageId() == null || context.stageId() <= 0
                || !Set.of("REFERENCE_EXISTING", "READ_ONLY_AGGREGATE").contains(String.valueOf(context.instanceResolutionStrategy())))
            throw exception(FORBIDDEN);
        authorize(context.tenantId(), context.actorId(), context.projectId());
        return new StageBusinessViewProvider.Result(Set.of("QUERY"));
    }

    @Override public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context) {
        inspectContext(context);
        return page(context.tenantId(), context.projectId(), null, 100).stream().map(this::fact).toList();
    }

    @Override public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context, String objectId) {
        inspectContext(context);
        return fact(load(context.tenantId(), context.projectId(), parse(objectId), false));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context, String objectId, String expectedVersion) {
        inspectContext(context);
        var result = fact(load(context.tenantId(), context.projectId(), parse(objectId), true));
        if (!Objects.equals(result.factVersion(), expectedVersion)) throw unavailable();
        return result;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw unavailable();
        tenant(context.tenantId(), context.execution().projectId());
        var execution = executions.lockAndRevalidate(context.execution());
        return completion(load(context.tenantId(), execution.projectId(), parse(objectId), true), execution.executionId());
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw unavailable();
        tenant(context.tenantId(), context.execution().projectId());
        var execution = executions.lockAndRevalidateStage(context.execution());
        return completion(load(context.tenantId(), execution.projectId(), parse(objectId), true), execution.executionId());
    }

    @Override public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || !Set.of(objectType(), "PROJECT_" + objectType()).contains(String.valueOf(context.targetObjectKey()))
                || pageSize < 1 || pageSize > 100)
            throw exception(FORBIDDEN);
        tenant(context.tenantId(), context.projectId());
        return page(context.tenantId(), context.projectId(), afterObjectId == null ? null : parse(afterObjectId), pageSize)
                .stream().map(row -> new AssociationCandidate(row.id().toString(), version(row))).toList();
    }

    private record Snapshot(Long id, String name, String version, boolean completed) { }
    private String version(Snapshot row) { return objectType() + ":v1:" + row.version() + ":" + row.completed(); }
    private BusinessObjectFact fact(Snapshot row) {
        return new BusinessObjectFact(row.id().toString(), row.name(), version(row), Set.of("QUERY"),
                Map.of(FACT, row.completed()), List.of());
    }
    private CompletionFact completion(Snapshot row, Long executionId) {
        return new CompletionFact(row.id().toString(), version(row) + ":execution:" + executionId,
                row.completed(), Map.of(FACT, row.completed()));
    }
    private void authorize(Long tenant, Long actor, Long project) {
        tenant(tenant, project);
        if (actor == null || !Objects.equals(actor, SecurityFrameworkUtils.getLoginUserId())) throw exception(FORBIDDEN);
        var visible = scope.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, project, ProjectScopeApi.ACTION_VIEW));
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(project)
                || !permissions.hasAnyPermissions(actor, "pms:imp-stage-plan:query")) throw exception(FORBIDDEN);
    }
    private void tenant(Long tenant, Long project) {
        if (tenant == null || !Objects.equals(tenant, TenantContextHolder.getTenantId()) || project == null || project <= 0)
            throw exception(FORBIDDEN);
    }
    private Long parse(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) throw unavailable();
        try { return Long.valueOf(value); } catch (NumberFormatException invalid) { throw unavailable(); }
    }
    private IllegalArgumentException unavailable() { return new IllegalArgumentException("业务完成依据不存在、已失效或版本不匹配"); }

    private Snapshot load(Long tenant, Long project, Long id, boolean lock) {
        var plan = plans.selectByProjectId(tenant, project);
        if (lock && plan != null) plan = plans.selectForUpdate(
                new cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery(tenant, plan.getId()));
        var row = mapper.current(new StagePlanCompletionQuery(tenant, project, lock));
        if (row == null || !Objects.equals(row.getTenantId(), tenant) || !Objects.equals(row.getProjectId(), project)
                || !Objects.equals(row.getId(), id)) throw unavailable();
        boolean completed = Integer.valueOf(2).equals(row.getStatus()) && row.getEffectiveAt() != null
                && row.getBpmProcessInstanceId() != null && !row.getBpmProcessInstanceId().isBlank()
                && row.getDurationRevisionId() != null && plan != null
                && Objects.equals(plan.getTenantId(), tenant) && Objects.equals(plan.getProjectId(), project)
                && Objects.equals(plan.getCurrentDurationRevisionId(), row.getDurationRevisionId());
        return new Snapshot(row.getId(), "施工计划审批批次 " + row.getId(),
                row.getVersion() + ":" + (plan == null ? "-" : plan.getVersion() + ":" + plan.getCurrentDurationRevisionId()), completed);
    }
    private List<Snapshot> page(Long tenant, Long project, Long after, int size) {
        if (after != null) return List.of();
        var row = mapper.current(new StagePlanCompletionQuery(tenant, project, false));
        return row == null ? List.of() : List.of(load(tenant, project, row.getId(), false));
    }
}
