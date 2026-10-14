package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.CutoverCoverageApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.stagebusiness.StageBusinessViewProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** 独立的项目范围聚合事实，不能用单个 CUTOVER_TASK 的成功替代。 */
@Service @RequiredArgsConstructor
public class CutoverCoverageCompletionProvider implements TaskBusinessObjectProvider, StageBusinessViewProvider {
    public static final String FACT = "CUTOVER_SCOPE_COVERED";
    private final CutoverCoverageApi coverage;
    private final ProjectScopeApi scopes;
    private final ProjectNodeExecutionApi executions;
    private final PermissionApi permissions;
    public String ownerContext() { return "IMP"; }
    public String objectType() { return "CUTOVER_COVERAGE"; }
    public Set<String> completionFactCodes() { return Set.of(FACT); }
    public Map<String, String> completionFactLabels() { return Map.of(FACT, "当前实施设备范围全部割接成功"); }
    public boolean supportsStageCompletionFacts() { return true; }
    public Set<String> inspectContext(TaskBusinessObjectProvider.Context context) {
        authorize(ProjectCurrentScopeQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.actorId())
                .anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build()); return Set.of("QUERY");
    }
    public StageBusinessViewProvider.Result inspectStage(StageBusinessViewProvider.Context context) {
        authorize(ProjectCurrentScopeQuery.builder()
                .tenantId(context.tenantId())
                .subjectUserId(context.actorId())
                .anchorProjectId(context.projectId())
                .actionCode(ProjectScopeApi.ACTION_VIEW).build()); return new StageBusinessViewProvider.Result(Set.of("QUERY"));
    }
    public List<BusinessObjectFact> candidates(TaskBusinessObjectProvider.Context context) {
        return List.of(inspect(context, context.projectId().toString()));
    }
    public BusinessObjectFact inspect(TaskBusinessObjectProvider.Context context, String objectId) {
        inspectContext(context); identity(context.projectId(), objectId);
        var value = coverage.inspect(new CutoverCoverageApi.Query(context.tenantId(), context.projectId()));
        return new BusinessObjectFact(objectId, "项目割接范围覆盖", value.factVersion(), Set.of("QUERY"), Map.of(FACT, value.complete()), List.of());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(TaskBusinessObjectProvider.Context context, String objectId, String expectedVersion) {
        inspectContext(context); identity(context.projectId(), objectId);
        var value = coverage.lockAndInspect(new CutoverCoverageApi.Query(context.tenantId(), context.projectId()));
        if (!Objects.equals(value.factVersion(), expectedVersion)) throw new IllegalArgumentException("项目割接覆盖事实已变化");
        return new BusinessObjectFact(objectId, "项目割接范围覆盖", value.factVersion(), Set.of("QUERY"), Map.of(FACT, value.complete()), List.of());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        var execution = executions.lockAndRevalidate(context.execution());
        return complete(new CutoverCoverageApi.Query(context.tenantId(), execution.projectId()), objectId, execution.executionId());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockStageCompletionFact(StageCompletionContext context, String objectId) {
        var execution = executions.lockAndRevalidateStage(context.execution());
        return complete(new CutoverCoverageApi.Query(context.tenantId(), execution.projectId()), objectId, execution.executionId());
    }
    public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        scope(context.tenantId(), context.projectId());
        if (!Set.of("CUTOVER_COVERAGE", "PROJECT_CUTOVER_COVERAGE").contains(context.targetObjectKey())
                || pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("割接覆盖绑定无效");
        if (afterObjectId != null) return List.of();
        var value = coverage.inspect(new CutoverCoverageApi.Query(context.tenantId(), context.projectId()));
        return List.of(new AssociationCandidate(context.projectId().toString(), value.factVersion()));
    }
    private CompletionFact complete(CutoverCoverageApi.Query query, String id, Long execution) {
        scope(query.tenantId(), query.projectId()); identity(query.projectId(), id);
        var value = coverage.lockAndInspect(query);
        return new CompletionFact(id, value.factVersion() + ":" + execution, value.complete(), Map.of(FACT, value.complete()));
    }
    private void authorize(ProjectCurrentScopeQuery query) {
        scope(query.tenantId(), query.anchorProjectId());
        if (query.subjectUserId() == null || !Objects.equals(query.subjectUserId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(query.subjectUserId(), "pms:cutover-task:query")
                || !scopes.resolveCurrent(query)
                    .fullProjectIds().contains(query.anchorProjectId())) throw new SecurityException("无权查看割接覆盖范围");
    }
    private void scope(Long tenant, Long project) {
        if (!Objects.equals(tenant, TenantContextHolder.getTenantId()) || project == null || project <= 0)
            throw new SecurityException("割接覆盖租户或项目无效");
    }
    private void identity(Long project, String id) {
        if (!Objects.equals(project.toString(), id)) throw new IllegalArgumentException("割接覆盖项目身份不一致");
    }
}
