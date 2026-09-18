package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import lombok.RequiredArgsConstructor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** A survey confirms once; archiving retains that result, but its mutable row is not a revision history. */
@Component
@RequiredArgsConstructor
public class SiteSurveyBusinessResultSource implements BusinessResultChangeSource {
    public static final Type TYPE = new Type("SOL", "SITE_SURVEY", "SURVEY_CONFIRMED");
    private static final Descriptor DESCRIPTOR = new Descriptor(TYPE, true, false, false);
    private final SiteSurveyEntityMapper surveys;

    @Override public Descriptor descriptor() { return DESCRIPTOR; }

    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (event == null || !TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType()))
            throw new IllegalArgumentException("RESULT_EVENT_TYPE_INVALID");
        if (event.revisionId() != null) throw new IllegalArgumentException("SURVEY_RESULT_EVENT_IDENTITY_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, event.objectId(), null);
    }

    @Override public Observation inspect(Query query) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        if (query.resultId() != null) throw new IllegalArgumentException("RESULT_LOOKUP_UNSUPPORTED");
        Long objectId = BusinessResultSource.nativeId(query.objectId());
        var row = surveys.selectById(objectId);
        if (row == null || Boolean.TRUE.equals(row.getDeleted()))
            return Observation.absent(Status.NOT_FOUND, "RESULT_NOT_FOUND");
        if (!Objects.equals(query.tenantId(), row.getTenantId()) || !Objects.equals(query.projectId(), row.getProjectId())
                || !Objects.equals(objectId, row.getId())) throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        if (Integer.valueOf(0).equals(row.getStatus()) || Integer.valueOf(2).equals(row.getStatus()))
            return Observation.absent(Status.NOT_FORMED, "SURVEY_NOT_CONFIRMED");
        if ((!Integer.valueOf(1).equals(row.getStatus()) && !Integer.valueOf(3).equals(row.getStatus()))
                || row.getConfirmedAt() == null || row.getVersion() == null || row.getVersion() < 0)
            return Observation.absent(Status.UNAVAILABLE, "SURVEY_RESULT_INCONSISTENT");
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, row.getId().toString(),
                row.getId().toString(), null, row.getVersion().toString(), Validity.CURRENT, row.getConfirmedAt()));
    }
}
