package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_VERSION_NOT_MATCH;

/**
 * PRE-01: read-only node reference to the project's effective duration baseline.
 * The original controller/service remains the only business command entry; no implicit creation.
 */
@Service
@RequiredArgsConstructor
public class ConstructionPlanTaskBusinessObjectProvider implements TaskBusinessObjectProvider, StageBusinessViewProvider {

    public static final String DURATION_EFFECTIVE = "DURATION_EFFECTIVE";
    public static final String TARGET_OBJECT_KEY = "CONSTRUCTION_PLAN";

    private final ConstructionPlanMapper planMapper;
    private final ConstructionPlanRevisionMapper revisionMapper;
    private final ProjectScopeApi projectScopeApi;
    private final PermissionApi permissionApi;
    private final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.tenantId() == null || context.execution() == null
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        var execution = executions.lockAndRevalidate(context.execution());
        return lockedResult(planForUpdate(context.tenantId(), execution.projectId(), objectId), execution.executionId());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) {
        if (context == null || context.tenantId() == null || context.execution() == null
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        var execution = executions.lockAndRevalidateStage(context.execution());
        return lockedResult(planForUpdate(context.tenantId(), execution.projectId(), objectId), execution.executionId());
    }

    private CompletionFact lockedResult(ConstructionPlanDO plan, Long executionId) {
        var revision = currentRevision(plan);
        boolean effective = revision != null;
        return new CompletionFact(plan.getId().toString(), "PLN_CONSTRUCTION_PLAN:v1:" + plan.getVersion()
                + ":" + plan.getPlanRecalculationStatusCode() + ":"
                + (revision == null ? "-" : revision.getId() + ":" + revision.getVersion())
                + ":execution:" + executionId, effective,
                Map.of(DURATION_EFFECTIVE, effective));
    }

    @Override
    public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || !TARGET_OBJECT_KEY.equals(context.targetObjectKey()) || pageSize < 1 || pageSize > 100) {
            throw exception(FORBIDDEN);
        }
        if (afterObjectId != null) return List.of();
        var plan = planMapper.selectByProjectId(context.tenantId(), context.projectId());
        if (plan == null) return List.of();
        return List.of(new AssociationCandidate(plan.getId().toString(), factVersion(plan, currentRevision(plan))));
    }

    @Override
    public String ownerContext() { return "PLN"; }

    @Override
    public String objectType() { return "CONSTRUCTION_PLAN"; }

    @Override
    public Set<String> completionFactCodes() { return Set.of(DURATION_EFFECTIVE); }

    @Override
    public boolean supportsStageCompletionFacts() { return true; }

    @Override
    public Map<String, String> completionFactLabels() {
        return Map.of(DURATION_EFFECTIVE, "当前有效工期基线已生效");
    }

    @Override
    public StageBusinessViewProvider.Result inspectStage(StageBusinessViewProvider.Context context) {
        if (context == null || context.stageId() == null || context.stageId() <= 0
                || !Set.of("REFERENCE_EXISTING", "CREATE_ON_FIRST_ACTION", "READ_ONLY_AGGREGATE").contains(context.instanceResolutionStrategy())) {
            throw exception(FORBIDDEN);
        }
        requireProjectQuery(context.tenantId(), context.actorId(), context.projectId());
        Set<String> actions = new LinkedHashSet<>(Set.of("QUERY"));
        var execution = context.execution();
        if (!"READ_ONLY_AGGREGATE".equals(context.instanceResolutionStrategy()) && execution != null && execution.writable()
                && Objects.equals(context.projectId(), execution.projectId()) && Objects.equals(context.stageId(), execution.stageId())
                && hasScope(context.tenantId(), context.actorId(), context.projectId(), ProjectScopeApi.ACTION_MANAGE)
                && permissionApi.hasAnyPermissions(context.actorId(), ConstructionPlanApplicationService.PERMISSION_MANAGE)) {
            actions.addAll(Set.of("LINK", "UNLINK", "CREATE", "UPDATE"));
        }
        return new StageBusinessViewProvider.Result(actions);
    }

    @Override
    public Set<String> inspectContext(TaskBusinessObjectProvider.Context context) {
        requireQuery(context);
        Set<String> actions = new LinkedHashSet<>(Set.of("QUERY"));
        if (canManage(context)) actions.addAll(Set.of("LINK", "UNLINK", "CREATE", "UPDATE"));
        return Set.copyOf(actions);
    }

    @Override
    public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context) {
        requireQuery(context);
        var plan = planMapper.selectByProjectId(context.tenantId(), context.projectId());
        if (plan == null) return List.of();
        return List.of(toFact(context, plan));
    }

    @Override
    public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context, String objectId) {
        requireQuery(context);
        return toFact(context, requirePlan(selectPlan(context, objectId)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context, String objectId, String expectedVersion) {
        requireQuery(context);
        BusinessObjectFact fact = toFact(context, requirePlan(planForUpdate(context.tenantId(), context.projectId(), objectId)));
        if (expectedVersion == null || !expectedVersion.equals(fact.factVersion())) {
            throw exception(CONSTRUCTION_PLAN_VERSION_NOT_MATCH);
        }
        return fact;
    }

    private ConstructionPlanDO planForUpdate(Long tenantId, Long projectId, String objectId) {
        var plan = planMapper.selectForUpdate(new ConstructionPlanLockQuery(tenantId, parsePlanId(objectId)));
        if (plan == null || !Objects.equals(plan.getProjectId(), projectId)) throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        return plan;
    }

    private ConstructionPlanDO selectPlan(TaskBusinessObjectProvider.Context context, String objectId) {
        var plan = planMapper.selectByProjectId(context.tenantId(), context.projectId());
        if (plan == null || !Objects.equals(plan.getId(), parsePlanId(objectId))) throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        return plan;
    }

    private Long parsePlanId(String objectId) {
        if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        return Long.valueOf(objectId);
    }

    private ConstructionPlanDO requirePlan(ConstructionPlanDO plan) {
        if (plan == null || plan.getId() == null || plan.getVersion() == null) throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        return plan;
    }

    /** The current duration baseline exists only while an approved effective revision is attached. */
    private ConstructionPlanRevisionDO currentRevision(ConstructionPlanDO plan) {
        if (plan == null || plan.getCurrentDurationRevisionId() == null) return null;
        var revision = revisionMapper.selectById(new ConstructionPlanRevisionLockQuery(
                plan.getTenantId(), plan.getId(), plan.getCurrentDurationRevisionId()));
        return revision == null || !Objects.equals(revision.getPlanId(), plan.getId()) ? null : revision;
    }

    private BusinessObjectFact toFact(TaskBusinessObjectProvider.Context context, ConstructionPlanDO plan) {
        requirePlan(plan);
        if (!Objects.equals(context.tenantId(), plan.getTenantId())
                || !Objects.equals(context.projectId(), plan.getProjectId())) {
            throw exception(CONSTRUCTION_PLAN_NOT_EXISTS);
        }
        var revision = currentRevision(plan);
        Set<String> actions = new LinkedHashSet<>();
        actions.add("QUERY");
        if (canManage(context)) {
            actions.addAll(Set.of("LINK", "UNLINK", "CREATE", "UPDATE"));
        }
        return new BusinessObjectFact(plan.getId().toString(), "项目工期基线 V" + (revision == null ? "-" : revision.getRevisionNo()),
                factVersion(plan, revision), actions, Map.of(DURATION_EFFECTIVE, revision != null), List.of());
    }

    private String factVersion(ConstructionPlanDO plan, ConstructionPlanRevisionDO revision) {
        return "PLN_CONSTRUCTION_PLAN:v1:" + plan.getVersion() + ":" + plan.getPlanRecalculationStatusCode()
                + ":" + (revision == null ? "-" : revision.getId() + ":" + revision.getVersion());
    }

    private void requireQuery(TaskBusinessObjectProvider.Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0) throw exception(FORBIDDEN);
        requireProjectQuery(context.tenantId(), context.actorId(), context.projectId());
    }

    private void requireProjectQuery(Long tenantId, Long actorId, Long projectId) {
        if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0 || projectId == null || projectId <= 0
                || !Objects.equals(tenantId, TenantContextHolder.getTenantId())
                || !Objects.equals(actorId, SecurityFrameworkUtils.getLoginUserId())) {
            throw exception(FORBIDDEN);
        }
        if (!hasScope(tenantId, actorId, projectId, ProjectScopeApi.ACTION_VIEW)
                || !permissionApi.hasAnyPermissions(actorId, "pms:construction-plan:query", ConstructionPlanApplicationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean canManage(TaskBusinessObjectProvider.Context context) {
        return hasScope(context.tenantId(), context.actorId(), context.projectId(), ProjectScopeApi.ACTION_MANAGE)
                && permissionApi.hasAnyPermissions(context.actorId(), ConstructionPlanApplicationService.PERMISSION_MANAGE);
    }

    private boolean hasScope(Long tenantId, Long actorId, Long projectId, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, action));
        return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(projectId);
    }
}
