package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.ArrivalDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.ArrivalTaskFactMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalTaskFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrival.query.ArrivalTaskPageQuery;
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

/** Read-only signed-receipt facts over the existing arrival Owner. */
@Service
@RequiredArgsConstructor
public class ArrivalTaskBusinessObjectProvider implements TaskBusinessObjectProvider {
    public static final String FACT = "ARRIVAL_SIGNED";
    private final ArrivalTaskFactMapper records;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectNodeExecutionApi executions;

    @Override public String ownerContext() { return "IMP"; }
    @Override public String objectType() { return "ARRIVAL"; }
    @Override public Set<String> completionFactCodes() { return Set.of(FACT); }
    @Override public Map<String, String> completionFactLabels() { return Map.of(FACT, "到货记录已正式签收"); }
    @Override public Set<String> inspectContext(Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0 || context.actorId() == null) throw denied();
        var query = new ArrivalTaskFactQuery(context.tenantId(), context.projectId(), null);
        scope(query);
        if (!Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(context.actorId(), "pms:imp-arrival:query")) throw denied();
        var visible = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(context.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(context.projectId())) throw denied();
        return Set.of("QUERY");
    }
    @Override public List<BusinessObjectFact> candidates(Context context) {
        inspectContext(context);
        return records.selectAssociationPage(new ArrivalTaskPageQuery(context.tenantId(), context.projectId(), null, 100))
                .stream().map(row -> fact(require(row, new ArrivalTaskFactQuery(context.tenantId(), context.projectId(), row.getId())))).toList();
    }
    @Override public BusinessObjectFact inspect(Context context, String objectId) {
        inspectContext(context);
        var query = new ArrivalTaskFactQuery(context.tenantId(), context.projectId(), parse(objectId));
        return fact(require(records.selectTaskFact(query), query));
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        inspectContext(context);
        var query = new ArrivalTaskFactQuery(context.tenantId(), context.projectId(), parse(objectId));
        var value = fact(require(records.selectTaskFactForUpdate(query), query));
        if (!Objects.equals(value.factVersion(), expectedVersion)) throw new IllegalArgumentException("ARRIVAL_FACT_STALE");
        return value;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw denied();
        scope(new ArrivalTaskFactQuery(context.tenantId(), context.execution().projectId(), null));
        var execution = executions.lockAndRevalidate(context.execution());
        var query = new ArrivalTaskFactQuery(context.tenantId(), execution.projectId(), parse(objectId));
        var row = require(records.selectTaskFactForUpdate(query), query);
        return new CompletionFact(objectId, version(row) + ":execution:" + execution.executionId(), confirmed(row), Map.of(FACT, confirmed(row)));
    }
    @Override public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || pageSize < 1 || pageSize > 100
                || !Set.of("ARRIVAL", "PROJECT_ARRIVAL").contains(String.valueOf(context.targetObjectKey()))) throw denied();
        scope(new ArrivalTaskFactQuery(context.tenantId(), context.projectId(), null));
        return records.selectAssociationPage(new ArrivalTaskPageQuery(context.tenantId(), context.projectId(),
                        afterObjectId == null ? null : parse(afterObjectId), pageSize)).stream()
                .map(row -> { require(row, new ArrivalTaskFactQuery(context.tenantId(), context.projectId(), row.getId()));
                    return new AssociationCandidate(row.getId().toString(), version(row)); }).toList();
    }
    private ArrivalDO require(ArrivalDO row, ArrivalTaskFactQuery query) {
        scope(query);
        if (row == null || !Objects.equals(row.getId(), query.arrivalId()) || !Objects.equals(row.getTenantId(), query.tenantId())
                || !Objects.equals(row.getProjectId(), query.projectId()) || Boolean.TRUE.equals(row.getDeleted())
                || row.getVersion() == null || row.getStatus() == null || row.getStatus() < 0 || row.getStatus() > 2)
            throw new IllegalArgumentException("ARRIVAL_FACT_UNAVAILABLE");
        return row;
    }
    private BusinessObjectFact fact(ArrivalDO row) {
        return new BusinessObjectFact(row.getId().toString(), row.getCode(), version(row), Set.of("QUERY"), Map.of(FACT, confirmed(row)), List.of());
    }
    private boolean confirmed(ArrivalDO row) { return Objects.equals(1, row.getStatus()); }
    private String version(ArrivalDO row) { return "ARRIVAL:" + row.getId() + ":" + row.getVersion() + ":" + row.getStatus(); }
    private void scope(ArrivalTaskFactQuery query) {
        if (query.tenantId() == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw denied();
    }
    private Long parse(String objectId) {
        try { if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw denied(); return Long.valueOf(objectId); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("ARRIVAL_ID_INVALID"); }
    }
    private SecurityException denied() { return new SecurityException("无权读取项目到货签收事实"); }
}
