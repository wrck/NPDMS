package cn.iocoder.yudao.module.pms.engineering.service.stageplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.StagePlanCompletionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.completion.StagePlanCompletionQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanLockQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 审批批次自身是不可变成果身份；仅最新生效且工期仍一致的批次可充当当前交付证据。 */
@Component
@RequiredArgsConstructor
public class StagePlanBusinessResultSource implements BusinessResultChangeSource, BusinessResultInventorySource {
    public static final Type TYPE = new Type("PLN", "STAGE_PLAN", "CONSTRUCTION_PLAN_APPROVED");
    private final StagePlanCompletionMapper batches;
    private final ConstructionPlanMapper plans;

    @Override public Descriptor descriptor() { return new Descriptor(TYPE, true, true, false); }
    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (!TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType())
                || event.revisionId() != null) throw new IllegalArgumentException("RESULT_EVENT_TYPE_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, event.objectId(), null);
    }
    @Override public Observation inspect(Query query) { return inspect(query, false); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Observation lockAndInspect(Query query) { return inspect(query, true); }

    private Observation inspect(Query query, boolean lock) {
        scope(query.tenantId(), query.projectId(), query.type());
        Long id = BusinessResultSource.nativeId(query.objectId() == null ? query.resultId() : query.objectId());
        if (query.resultId() != null && !Objects.equals(id, BusinessResultSource.nativeId(query.resultId())))
            throw new IllegalArgumentException("RESULT_IDENTITY_MISMATCH");
        var plan = plans.selectByProjectId(query.tenantId(), query.projectId());
        if (lock && plan != null) plan = plans.selectForUpdate(new ConstructionPlanLockQuery(query.tenantId(), plan.getId()));
        var row = batches.byId(new StagePlanCompletionQuery(query.tenantId(), query.projectId(), id, lock));
        if (row == null) return Observation.absent(Status.NOT_FORMED, "CONSTRUCTION_PLAN_NOT_APPROVED");
        if (!Objects.equals(row.getTenantId(), query.tenantId()) || !Objects.equals(row.getProjectId(), query.projectId())
                || !Objects.equals(row.getId(), id)) throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        if (!Integer.valueOf(2).equals(row.getStatus())) return Observation.absent(Status.NOT_FORMED, "CONSTRUCTION_PLAN_NOT_APPROVED");
        if (row.getEffectiveAt() == null || row.getBpmProcessInstanceId() == null || row.getBpmProcessInstanceId().isBlank()
                || row.getVersion() == null || row.getVersion() < 0 || row.getDurationRevisionId() == null)
            return Observation.absent(Status.UNAVAILABLE, "CONSTRUCTION_PLAN_RESULT_INCONSISTENT");
        var current = batches.current(new StagePlanCompletionQuery(query.tenantId(), query.projectId(), null, lock));
        boolean valid = current != null && Objects.equals(current.getId(), id) && plan != null
                && Objects.equals(plan.getTenantId(), query.tenantId()) && Objects.equals(plan.getProjectId(), query.projectId())
                && Objects.equals(plan.getCurrentDurationRevisionId(), row.getDurationRevisionId());
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, id.toString(), id.toString(),
                null, row.getVersion() + ":" + (plan == null ? "-" : plan.getVersion()),
                valid ? Validity.CURRENT : Validity.NOT_CURRENT, row.getEffectiveAt()));
    }

    @Override public InventoryPage inventory(InventoryQuery query) {
        scope(query.tenantId(), query.projectId(), query.type());
        if (query.historical()) throw new IllegalArgumentException("RESULT_HISTORY_UNSUPPORTED");
        var objects = BusinessResultInventorySource.nativeObjects(query);
        var after = BusinessResultSource.nativeId(query.after());
        var row = batches.current(new StagePlanCompletionQuery(query.tenantId(), query.projectId(), null, false));
        if (row == null || after != null && row.getId() <= after || objects != null && !objects.contains(row.getId()))
            return new InventoryPage(query.after(), true, List.of());
        return new InventoryPage(row.getId().toString(), true, List.of(inspect(new Query(query.tenantId(), query.projectId(), TYPE, row.getId().toString(), null))));
    }
    private void scope(Long tenant, Long project, Type type) {
        if (!TYPE.equals(type) || !Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || project == null || project <= 0)
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
    }
}
