package cn.iocoder.yudao.module.pms.engineering.service.solution;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query.SolutionResultInventoryQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.query.SolutionResultLockQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 实施方案业务成果源：审批通过（状态 3）是唯一结果事实，基线版本号是该事实的业务修订锚。
 * <p>
 * 方案行为单行可变记录，无修订历史；撤回、终止或再编辑后重新报批会替换既有批准事实
 * （基线版本与批准时间同时前移），已关联的交付件证据按复合身份与修订锚重验。
 */
@Component
public class ImplementationSolutionBusinessResultSource implements BusinessResultChangeSource, BusinessResultInventorySource {

    public static final Type TYPE = new Type("SOL", "IMPLEMENTATION_SOLUTION", "IMPLEMENTATION_PLAN_APPROVED");
    private static final Descriptor DESCRIPTOR = new Descriptor(TYPE, true, false, false);

    private final SolutionMapper solutions;

    public ImplementationSolutionBusinessResultSource(SolutionMapper solutions) {
        this.solutions = solutions;
    }

    @Override public DeliveryIdentity deliveryIdentity(Query query) {
        var observation=lockAndInspect(query);var result=observation.result();
        if(result==null || result.validity()!=Validity.CURRENT)return null;
        return new DeliveryIdentity("SOL","solution",BusinessResultSource.nativeId(result.objectId()),"IMPLEMENTATION_PLAN",
                "solution",result.objectId(),Long.valueOf(result.businessRevision()));
    }

    @Override public Descriptor descriptor() { return DESCRIPTOR; }

    /** 批准/撤回/再批准均在 Owner 事务内经既有 changed() 通道写入同一事件流。 */
    @Override public boolean transactionalChangeCoverage() { return true; }

    @Override
    @Transactional(readOnly = true)
    public InventoryPage inventory(InventoryQuery query) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        if (query.historical() && !DESCRIPTOR.historicalLookup())
            throw new IllegalArgumentException("RESULT_HISTORY_UNSUPPORTED");
        var ids = solutions.selectResultInventory(new SolutionResultInventoryQuery(query.tenantId(), query.projectId(),
                BusinessResultInventorySource.nativeObjects(query), BusinessResultSource.nativeId(query.after()),
                query.limit() + 1, query.historical()));
        return BusinessResultInventorySource.nativePage(query, ids,
                id -> inspect(new Query(query.tenantId(), query.projectId(), TYPE, id, null)));
    }

    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (event == null || !TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType()))
            throw new IllegalArgumentException("RESULT_EVENT_TYPE_INVALID");
        if (event.revisionId() != null) throw new IllegalArgumentException("SOLUTION_RESULT_EVENT_IDENTITY_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, event.objectId(), null);
    }

    @Override public Observation inspect(Query query) {
        return inspect(query, false);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Observation lockAndInspect(Query query) { return inspect(query, true); }

    private Observation inspect(Query query, boolean lock) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        if (query.resultId() != null) throw new IllegalArgumentException("RESULT_LOOKUP_UNSUPPORTED");
        Long objectId = BusinessResultSource.nativeId(query.objectId());
        var row = lock ? solutions.selectResultForUpdate(
                new SolutionResultLockQuery(query.tenantId(), query.projectId(), objectId))
                : solutions.selectById(objectId);
        if (row == null || Boolean.TRUE.equals(row.getDeleted()))
            return Observation.absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        if (!Objects.equals(query.tenantId(), row.getTenantId()) || !Objects.equals(query.projectId(), row.getProjectId())
                || !Objects.equals(objectId, row.getId())) throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        if (!Integer.valueOf(3).equals(row.getStatus()))
            return Observation.absent(Status.NOT_FORMED, "IMPLEMENTATION_PLAN_NOT_APPROVED");
        if (row.getApprovedTime() == null || row.getBaselineVersion() == null
                || row.getVersion() == null || row.getVersion() < 0)
            return Observation.absent(Status.UNAVAILABLE, "IMPLEMENTATION_PLAN_RESULT_INCONSISTENT");
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, row.getId().toString(),
                row.getId().toString(), row.getBaselineVersion().toString(), row.getVersion().toString(),
                Validity.CURRENT, row.getApprovedTime()));
    }
}
