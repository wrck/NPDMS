package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.CutoverApprovalFactApi;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.completion.CutoverCompletionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.completion.CutoverCompletionQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverPlanRevisionQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
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

/** Current P5 approval projection of a CUT task; the existing P6 completion provider is unchanged. */
@Service
@RequiredArgsConstructor
public class CutoverApprovalTaskBusinessObjectProvider implements TaskBusinessObjectProvider {
    public static final String FACT = "CUTOVER_APPROVED";
    private final CutoverCompletionMapper tasks;
    private final CutoverPlanRevisionMapper plans;
    private final CutoverApprovalFactApi approvals;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectNodeExecutionApi executions;

    @Override public String ownerContext() { return "CUT"; }
    @Override public String objectType() { return "CUTOVER_APPROVAL"; }
    @Override public Set<String> completionFactCodes() { return Set.of(FACT); }
    @Override public Map<String, String> completionFactLabels() { return Map.of(FACT, "当前割接任务方案审批通过（不代表割接成功）"); }
    @Override public Set<String> inspectContext(Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0 || context.actorId() == null) throw denied();
        scope(new CutoverCompletionQuery(context.tenantId(), context.projectId(), null, null, 1, false));
        if (!Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(context.actorId(), "pms:cutover-task:query")) throw denied();
        var visible = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(context.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(context.projectId())) throw denied();
        return Set.of("QUERY");
    }
    @Override public List<BusinessObjectFact> candidates(Context context) {
        inspectContext(context);
        return tasks.page(new CutoverCompletionQuery(context.tenantId(), context.projectId(), null, null, 100, false)).stream()
                .map(row -> fact(load(new CutoverCompletionQuery(context.tenantId(), context.projectId(), row.getId(), null, 1, false)))).toList();
    }
    @Override public BusinessObjectFact inspect(Context context, String objectId) {
        inspectContext(context);
        return fact(load(new CutoverCompletionQuery(context.tenantId(), context.projectId(), parse(objectId), null, 1, false)));
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        inspectContext(context);
        var value = fact(load(new CutoverCompletionQuery(context.tenantId(), context.projectId(), parse(objectId), null, 1, true)));
        if (!Objects.equals(value.factVersion(), expectedVersion)) throw new IllegalArgumentException("CUTOVER_APPROVAL_FACT_STALE");
        return value;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw denied();
        scope(new CutoverCompletionQuery(context.tenantId(), context.execution().projectId(), null, null, 1, true));
        var execution = executions.lockAndRevalidate(context.execution());
        var value = load(new CutoverCompletionQuery(context.tenantId(), execution.projectId(), parse(objectId), null, 1, true));
        return new CompletionFact(objectId, value.version() + ":execution:" + execution.executionId(), value.approved(), Map.of(FACT, value.approved()));
    }
    @Override public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || pageSize < 1 || pageSize > 100
                || !Set.of("CUTOVER_APPROVAL", "PROJECT_CUTOVER_APPROVAL").contains(String.valueOf(context.targetObjectKey()))) throw denied();
        var query = new CutoverCompletionQuery(context.tenantId(), context.projectId(), null,
                afterObjectId == null ? null : parse(afterObjectId), pageSize, false);
        scope(query);
        return tasks.page(query).stream().map(row -> {
            var value = load(new CutoverCompletionQuery(context.tenantId(), context.projectId(), row.getId(), null, 1, false));
            return new AssociationCandidate(value.id().toString(), value.version());
        }).toList();
    }

    private record Current(Long id, String name, String version, boolean approved) { }

    private Current load(CutoverCompletionQuery query) {
        scope(query);
        var task = tasks.one(query);
        if (task == null || !Objects.equals(task.getId(), query.objectId()) || !Objects.equals(task.getTenantId(), query.tenantId())
                || !Objects.equals(task.getProjectId(), query.projectId()) || Boolean.TRUE.equals(task.getDeleted()) || task.getVersion() == null)
            throw new IllegalArgumentException("CUTOVER_TASK_UNAVAILABLE");
        var planQuery = new CutoverPlanRevisionQuery(query.tenantId(), task.getId(), null);
        var plan = query.lock() ? plans.selectCurrentForUpdate(planQuery) : plans.selectCurrent(planQuery);
        String version = "CUTOVER_APPROVAL:" + task.getId() + ":" + task.getVersion();
        if (plan == null) return new Current(task.getId(), task.getTaskName(), version + ":NO_PLAN", false);
        if (!Objects.equals(plan.getTenantId(), query.tenantId()) || !Objects.equals(plan.getCutoverTaskId(), task.getId())
                || plan.getVersion() == null || Boolean.TRUE.equals(plan.getDeleted())) throw new IllegalArgumentException("CUTOVER_PLAN_SCOPE_INVALID");
        version += ":plan:" + plan.getId() + ":" + plan.getVersion() + ":" + plan.getStatusCode();
        if (!"SUBMITTED".equals(plan.getStatusCode()) || !Integer.valueOf(1).equals(plan.getCurrentMarker()))
            return new Current(task.getId(), task.getTaskName(), version + ":PLAN_NOT_CURRENT_SUBMITTED", false);
        var observation = approvals.inspect(new CutoverApprovalFactQuery(query.tenantId(), task.getId(), plan.getId()));
        if (observation.status() == InspectStatus.NOT_FOUND) return new Current(task.getId(), task.getTaskName(), version + ":NO_APPROVAL", false);
        var fact = observation.fact();
        if (fact == null || !Objects.equals(fact.taskId(), task.getId()) || !Objects.equals(fact.planRevisionId(), plan.getId()))
            throw new IllegalArgumentException("CUTOVER_APPROVAL_SCOPE_INVALID");
        if (query.lock()) {
            var checked = approvals.lockAndRevalidate(new CutoverApprovalRevalidationQuery(query.tenantId(), new ExpectedCutoverApprovalFact(
                    fact.approvalInstanceId(), fact.approvalVersion(), fact.taskId(), fact.planRevisionId(), fact.planRevisionNo(), fact.status(),
                    fact.sourceSnapshotVersion(), fact.replacementApprovalInstanceId(), fact.decisionAt(), fact.rejectionReason())));
            if (checked.status() != RevalidationStatus.VALID) throw new IllegalArgumentException("CUTOVER_APPROVAL_FACT_STALE");
            fact = checked.currentFact();
        }
        boolean approved = fact.status() == ApprovalStatus.APPROVED && fact.decisionAt() != null && fact.replacementApprovalInstanceId() == null;
        return new Current(task.getId(), task.getTaskName(), version + ":approval:" + fact.approvalInstanceId() + ":"
                + fact.approvalVersion() + ":" + fact.status(), approved);
    }
    private BusinessObjectFact fact(Current value) {
        return new BusinessObjectFact(value.id().toString(), value.name(), value.version(), Set.of("QUERY"), Map.of(FACT, value.approved()), List.of());
    }
    private void scope(CutoverCompletionQuery query) {
        if (query.tenantId() == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw denied();
    }
    private Long parse(String objectId) {
        try { if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw denied(); return Long.valueOf(objectId); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("CUTOVER_TASK_ID_INVALID"); }
    }
    private SecurityException denied() { return new SecurityException("无权读取项目割接审批事实"); }
}
