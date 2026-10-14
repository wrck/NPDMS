package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training.TrainingDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.TrainingTaskFactMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingTaskFactQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.training.query.TrainingTaskPageQuery;
import cn.iocoder.yudao.module.pms.engineering.enums.TrainingStatusEnum;
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

/** Read-only customer-confirmation facts over the existing training Owner. */
@Service
@RequiredArgsConstructor
public class TrainingTaskBusinessObjectProvider implements TaskBusinessObjectProvider {
    public static final String FACT = "TRAINING_CONFIRMED";
    private final TrainingTaskFactMapper records;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectNodeExecutionApi executions;

    @Override public String ownerContext() { return "IMP"; }
    @Override public String objectType() { return "TRAINING"; }
    @Override public Set<String> completionFactCodes() { return Set.of(FACT); }
    @Override public Map<String, String> completionFactLabels() { return Map.of(FACT, "培训记录已由客户确认"); }
    @Override public Set<String> inspectContext(Context context) {
        if (context == null || context.taskId() == null || context.taskId() <= 0 || context.actorId() == null) throw denied();
        var query = new TrainingTaskFactQuery(context.tenantId(), context.projectId(), null);
        scope(query);
        if (!Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(context.actorId(), "pms:imp-training:query")) throw denied();
        var visible = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(context.projectId()).actionCode(ProjectScopeApi.ACTION_VIEW).build());
        if (visible == null || visible.fullProjectIds() == null || !visible.fullProjectIds().contains(context.projectId())) throw denied();
        return Set.of("QUERY");
    }
    @Override public List<BusinessObjectFact> candidates(Context context) {
        inspectContext(context);
        return records.selectAssociationPage(new TrainingTaskPageQuery(context.tenantId(), context.projectId(), null, 100))
                .stream().map(row -> fact(require(row, new TrainingTaskFactQuery(context.tenantId(), context.projectId(), row.getId())))).toList();
    }
    @Override public BusinessObjectFact inspect(Context context, String objectId) {
        inspectContext(context);
        var query = new TrainingTaskFactQuery(context.tenantId(), context.projectId(), parse(objectId));
        return fact(require(records.selectTaskFact(query), query));
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        inspectContext(context);
        var query = new TrainingTaskFactQuery(context.tenantId(), context.projectId(), parse(objectId));
        var value = fact(require(records.selectTaskFactForUpdate(query), query));
        if (!Objects.equals(value.factVersion(), expectedVersion)) throw new IllegalArgumentException("TRAINING_FACT_STALE");
        return value;
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CompletionFact lockCompletionFact(CompletionContext context, String objectId) {
        if (context == null || context.execution() == null) throw denied();
        scope(new TrainingTaskFactQuery(context.tenantId(), context.execution().projectId(), null));
        var execution = executions.lockAndRevalidate(context.execution());
        var query = new TrainingTaskFactQuery(context.tenantId(), execution.projectId(), parse(objectId));
        var row = require(records.selectTaskFactForUpdate(query), query);
        return new CompletionFact(objectId, version(row) + ":execution:" + execution.executionId(), confirmed(row), Map.of(FACT, confirmed(row)));
    }
    @Override public List<AssociationCandidate> associationCandidates(AssociationContext context, String afterObjectId, int pageSize) {
        if (context == null || pageSize < 1 || pageSize > 100
                || !Set.of("TRAINING", "PROJECT_TRAINING").contains(String.valueOf(context.targetObjectKey()))) throw denied();
        scope(new TrainingTaskFactQuery(context.tenantId(), context.projectId(), null));
        return records.selectAssociationPage(new TrainingTaskPageQuery(context.tenantId(), context.projectId(),
                        afterObjectId == null ? null : parse(afterObjectId), pageSize)).stream()
                .map(row -> { require(row, new TrainingTaskFactQuery(context.tenantId(), context.projectId(), row.getId()));
                    return new AssociationCandidate(row.getId().toString(), version(row)); }).toList();
    }
    private TrainingDO require(TrainingDO row, TrainingTaskFactQuery query) {
        scope(query);
        if (row == null || !Objects.equals(row.getId(), query.trainingId()) || !Objects.equals(row.getTenantId(), query.tenantId())
                || !Objects.equals(row.getProjectId(), query.projectId()) || Boolean.TRUE.equals(row.getDeleted())
                || row.getVersion() == null || row.getStatus() == null || row.getStatus() < 0 || row.getStatus() > 3)
            throw new IllegalArgumentException("TRAINING_FACT_UNAVAILABLE");
        return row;
    }
    private BusinessObjectFact fact(TrainingDO row) {
        return new BusinessObjectFact(row.getId().toString(), row.getName(), version(row), Set.of("QUERY"), Map.of(FACT, confirmed(row)), List.of());
    }
    private boolean confirmed(TrainingDO row) { return Objects.equals(TrainingStatusEnum.CONFIRMED.getStatus(), row.getStatus()); }
    private String version(TrainingDO row) { return "TRAINING:" + row.getId() + ":" + row.getVersion() + ":" + row.getStatus(); }
    private void scope(TrainingTaskFactQuery query) {
        if (query.tenantId() == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId())
                || query.projectId() == null || query.projectId() <= 0) throw denied();
    }
    private Long parse(String objectId) {
        try { if (objectId == null || !objectId.matches("[1-9][0-9]*")) throw denied(); return Long.valueOf(objectId); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("TRAINING_ID_INVALID"); }
    }
    private SecurityException denied() { return new SecurityException("无权读取项目培训确认事实"); }
}
