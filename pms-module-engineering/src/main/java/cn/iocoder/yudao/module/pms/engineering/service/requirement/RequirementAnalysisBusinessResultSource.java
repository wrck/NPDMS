package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import lombok.RequiredArgsConstructor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import org.springframework.stereotype.Component;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementResultInventoryQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Frozen revision IDs remain stable when effective markers and optimistic versions later change. */
@Component
@RequiredArgsConstructor
public class RequirementAnalysisBusinessResultSource implements BusinessResultChangeSource, BusinessResultInventorySource {
    public static final Type TYPE = new Type("SOL", "REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED");
    private static final Descriptor DESCRIPTOR = new Descriptor(TYPE, true, true, true);
    private final RequirementAnalysisMapper revisions;

    @Override public Descriptor descriptor() { return DESCRIPTOR; }

    /** 原生结果形成/生效/撤销均在Owner事务内写入同一通道；草稿修改不改变该结果事实。 */
    @Override public boolean transactionalChangeCoverage() { return true; }

    @Override
    @Transactional(readOnly = true)
    public InventoryPage inventory(InventoryQuery query) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        if (query.historical() && !DESCRIPTOR.historicalLookup())
            throw new IllegalArgumentException("RESULT_HISTORY_UNSUPPORTED");
        var ids = revisions.selectResultInventory(new RequirementResultInventoryQuery(query.tenantId(), query.projectId(),
                BusinessResultInventorySource.nativeObjects(query), BusinessResultSource.nativeId(query.after()), query.limit() + 1, query.historical()));
        return BusinessResultInventorySource.nativePage(query, ids, id -> inspect(new Query(query.tenantId(), query.projectId(), TYPE, null, id)));
    }

    @Override public boolean declaresFormation(BusinessOperationResultEvent event) {
        // Activating or re-observing an existing frozen revision must never create a new formation boundary.
        return "OWNER.RequirementAnalysis.FORMED".equals(event.operationCode()) && TYPE.resultType().equals(event.resultCode());
    }

    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (event == null || !TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType()))
            throw new IllegalArgumentException("RESULT_EVENT_TYPE_INVALID");
        if (event.revisionId() == null || !event.revisionId().equals(event.objectId()))
            throw new IllegalArgumentException("REQUIREMENT_RESULT_EVENT_IDENTITY_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, null, event.revisionId());
    }

    @Override public Observation inspect(Query query) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        Long objectId = BusinessResultSource.nativeId(query.objectId());
        Long resultId = BusinessResultSource.nativeId(query.resultId());
        var row = resultId == null
                ? revisions.selectEffective(new RequirementProjectQuery(query.tenantId(), query.projectId()))
                : revisions.selectRevision(new RequirementRevisionQuery(query.tenantId(), resultId));
        if (row == null || Boolean.TRUE.equals(row.getDeleted()))
            return Observation.absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        if (!Objects.equals(query.tenantId(), row.getTenantId()) || !Objects.equals(query.projectId(), row.getProjectId())
                || objectId != null && !objectId.equals(row.getEntityId()) || resultId != null && !resultId.equals(row.getId()))
            throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        if ("DRAFT".equals(row.getRevisionState())) return Observation.absent(Status.NOT_FORMED, "REQUIREMENT_REVISION_NOT_FROZEN");
        if (!"FROZEN".equals(row.getRevisionState()) || !"COMPLETED".equals(row.getStatusCode())
                || row.getId() == null || row.getId() <= 0 || row.getEntityId() == null || row.getEntityId() <= 0
                || row.getRevisionNo() == null || row.getRevisionNo() <= 0 || row.getVersion() == null || row.getVersion() <= 0
                || row.getFrozenAt() == null || row.getEffectiveMarker() != null && row.getEffectiveMarker() != 1)
            return Observation.absent(Status.UNAVAILABLE, "REQUIREMENT_RESULT_INCONSISTENT");
        if (resultId == null && !row.effective()) return Observation.absent(Status.UNAVAILABLE, "CURRENT_REQUIREMENT_RESULT_MISMATCH");
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, row.getEntityId().toString(),
                row.getId().toString(), row.getRevisionNo().toString(), row.getVersion().toString(),
                row.effective() ? Validity.CURRENT : Validity.NOT_CURRENT, row.getFrozenAt()));
    }
}
