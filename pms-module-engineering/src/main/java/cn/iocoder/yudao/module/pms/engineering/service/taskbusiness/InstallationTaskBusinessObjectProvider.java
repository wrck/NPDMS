package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.installation.InstallationBusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource.InventoryQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Project aggregate over real current installation records; never a single-device completion shortcut. */
@Service
@RequiredArgsConstructor
public class InstallationTaskBusinessObjectProvider implements TaskBusinessObjectProvider {
    public static final String FACT = "PROJECT_INSTALLATION_COMPLETED";
    private final InstallationBusinessResultSource results;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectNodeExecutionApi executions;

    @Override public String ownerContext() { return "IMP"; }
    @Override public String objectType() { return "INSTALLATION"; }
    @Override public Set<String> completionFactCodes() { return Set.of(FACT); }
    @Override public Map<String, String> completionFactLabels() { return Map.of(FACT, "当前范围内全部设备安装完成"); }

    @Override public Set<String> inspectContext(Context context) {
        scope(new Scope(context.tenantId(), context.projectId()));
        if (context.taskId() == null || context.taskId() <= 0 || context.actorId() == null
                || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(context.actorId(), "pms:imp-installation:query")) throw denied();
        var visible = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(context.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(context.projectId())) throw denied();
        return Set.of("QUERY");
    }

    @Override @Transactional
    public List<BusinessObjectFact> candidates(Context context) {
        inspectContext(context);
        var current = current(new Scope(context.tenantId(), context.projectId()));
        return current == null ? List.of() : List.of(fact(current));
    }

    @Override @Transactional
    public BusinessObjectFact inspect(Context context, String objectId) {
        inspectContext(context);
        return fact(read(new BusinessResultSource.Query(context.tenantId(), context.projectId(),
                InstallationBusinessResultSource.TYPE, objectId, objectId)));
    }

    @Override @Transactional
    public BusinessObjectInspection inspectContextAndObjects(Context context, List<String> objectIds) {
        // This aggregate method is the external read entry; self-calls to inspect do not open a transaction.
        return TaskBusinessObjectProvider.super.inspectContextAndObjects(context, objectIds);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        var value = inspect(context, objectId);
        if (!Objects.equals(expectedVersion, value.factVersion())) throw new IllegalArgumentException("INSTALLATION_FACT_STALE");
        return value;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw denied();
        scope(new Scope(context.tenantId(), context.execution().projectId()));
        var execution = executions.lockAndRevalidate(context.execution());
        var value = read(new BusinessResultSource.Query(context.tenantId(), execution.projectId(),
                InstallationBusinessResultSource.TYPE, objectId, objectId));
        return new CompletionFact(objectId, value.version() + ":execution:" + execution.executionId(),
                value.completed(), Map.of(FACT, value.completed()));
    }

    @Override @Transactional
    public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || pageSize < 1 || pageSize > 100
                || !Set.of("INSTALLATION", "PROJECT_INSTALLATION").contains(String.valueOf(context.targetObjectKey()))) throw denied();
        var query = new Scope(context.tenantId(), context.projectId());
        scope(query);
        var value = current(query);
        if (value == null || afterObjectId != null
                && Long.parseLong(value.objectId()) <= Long.parseLong(afterObjectId)) return List.of();
        return List.of(new AssociationCandidate(value.objectId(), value.version()));
    }

    private record Scope(Long tenantId, Long projectId) { }
    private record Current(String objectId, boolean completed, String version) { }

    private Current current(Scope query) {
        scope(query);
        var page = results.inventory(new InventoryQuery(query.tenantId(), query.projectId(),
                InstallationBusinessResultSource.TYPE, false, null, null, 1));
        if (!page.complete() || page.observations().size() > 1) throw new IllegalStateException("INSTALLATION_RESULT_UNAVAILABLE");
        if (page.observations().isEmpty()) return null;
        var observed = page.observations().getFirst();
        if (observed.status() == BusinessResultSource.Status.UNAVAILABLE) throw new IllegalStateException("INSTALLATION_RESULT_UNAVAILABLE");
        var result = observed.result();
        if (result == null) return null;
        return value(query, observed);
    }

    private Current read(BusinessResultSource.Query query) {
        var scope = new Scope(query.tenantId(), query.projectId());
        scope(scope);
        var observation = results.lockAndInspect(query);
        if (observation.status() == BusinessResultSource.Status.UNAVAILABLE) throw new IllegalStateException("INSTALLATION_RESULT_UNAVAILABLE");
        if (observation.result() == null)
            return new Current(query.objectId(), false, "INSTALLATION:" + query.objectId() + ":" + observation.status() + ":" + observation.reason());
        return value(scope, observation);
    }

    private Current value(Scope query, BusinessResultSource.Observation observed) {
        var result = observed.result();
        if (!Objects.equals(result.tenantId(), query.tenantId()) || !Objects.equals(result.projectId(), query.projectId())
                || !InstallationBusinessResultSource.TYPE.equals(result.type()) || result.observationVersion() == null)
            throw new IllegalStateException("INSTALLATION_RESULT_SCOPE_MISMATCH");
        return new Current(result.objectId(), result.validity() == BusinessResultSource.Validity.CURRENT,
                "INSTALLATION:" + result.resultId() + ":" + result.observationVersion() + ":" + result.validity());
    }

    private BusinessObjectFact fact(Current value) {
        return new BusinessObjectFact(value.objectId(), "项目设备安装记录", value.version(), Set.of("QUERY"),
                Map.of(FACT, value.completed()), List.of());
    }
    private void scope(Scope query) {
        if (query.tenantId() == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw denied();
    }
    private SecurityException denied() { return new SecurityException("无权读取项目安装完成事实"); }
}
