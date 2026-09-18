package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Frozen revision IDs remain stable when effective markers and optimistic versions later change. */
@Component
@RequiredArgsConstructor
public class RequirementAnalysisBusinessResultSource implements BusinessResultSource {
    public static final Type TYPE = new Type("SOL", "REQUIREMENT_ANALYSIS", "REQUIREMENT_ANALYSIS_COMPLETED");
    private static final Descriptor DESCRIPTOR = new Descriptor(TYPE, true, true, true);
    private final RequirementAnalysisMapper revisions;

    @Override public Descriptor descriptor() { return DESCRIPTOR; }

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
