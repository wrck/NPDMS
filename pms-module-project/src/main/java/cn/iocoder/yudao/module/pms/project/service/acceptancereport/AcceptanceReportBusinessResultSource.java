package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** A published report is a report result, never an assertion that its acceptance activity completed. */
@Component
@RequiredArgsConstructor
public class AcceptanceReportBusinessResultSource implements BusinessResultSource {
    public static final Type TYPE = new Type("ACC", "ACCEPTANCE", "REPORT_VERSION_PUBLISHED");
    private static final Descriptor DESCRIPTOR = new Descriptor(TYPE, true, true, true);
    private final AcceptanceActivityMapper activities;
    private final AcceptanceReportVersionMapper reports;

    @Override public Descriptor descriptor() { return DESCRIPTOR; }

    @Override public Observation inspect(Query query) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        Long objectId = BusinessResultSource.nativeId(query.objectId());
        Long resultId = BusinessResultSource.nativeId(query.resultId());
        var report = resultId == null ? null : reports.selectById(resultId);
        if (resultId != null) {
            if (report == null || Boolean.TRUE.equals(report.getDeleted())) return missing();
            if (!Objects.equals(query.tenantId(), report.getTenantId()) || !Objects.equals(resultId, report.getId())
                    || report.getAcceptanceId() == null || report.getAcceptanceId() <= 0
                    || objectId != null && !objectId.equals(report.getAcceptanceId()))
                throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
            objectId = report.getAcceptanceId();
        }
        var activity = activities.selectById(objectId);
        if (activity == null || Boolean.TRUE.equals(activity.getDeleted())) return missing();
        if (!Objects.equals(query.tenantId(), activity.getTenantId()) || !Objects.equals(query.projectId(), activity.getProjectId())
                || !Objects.equals(objectId, activity.getId())) throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        if (report == null) {
            if (activity.getCurrentReportVersionId() == null)
                return Observation.absent(Status.NOT_FORMED, "NO_CURRENT_PUBLISHED_REPORT");
            report = reports.selectById(activity.getCurrentReportVersionId());
            if (report == null || Boolean.TRUE.equals(report.getDeleted())) return inconsistent();
            if (!Objects.equals(query.tenantId(), report.getTenantId()) || !Objects.equals(activity.getId(), report.getAcceptanceId())
                    || !Objects.equals(activity.getCurrentReportVersionId(), report.getId()))
                throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        }
        if ("DRAFT".equals(report.getReportStatus())) return resultId == null ? inconsistent()
                : Observation.absent(Status.NOT_FORMED, "REPORT_NOT_PUBLISHED");
        if (!Set.of("EFFECTIVE", "SUPERSEDED", "REVOKED").contains(String.valueOf(report.getReportStatus()))
                || report.getId() == null || report.getId() <= 0 || report.getEffectiveFrom() == null
                || report.getReportVersionNo() == null || report.getReportVersionNo() <= 0
                || activity.getVersion() == null || activity.getVersion() < 0) return inconsistent();
        boolean current = "EFFECTIVE".equals(report.getReportStatus());
        if (current != Objects.equals(report.getId(), activity.getCurrentReportVersionId())
                || current != (report.getEffectiveTo() == null) || resultId == null && !current)
            return inconsistent();
        Validity validity = current ? Validity.CURRENT : "REVOKED".equals(report.getReportStatus()) ? Validity.REVOKED : Validity.NOT_CURRENT;
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, activity.getId().toString(),
                report.getId().toString(), report.getReportVersionNo().toString(), activity.getVersion().toString(),
                validity, report.getEffectiveFrom()));
    }

    private Observation missing() { return Observation.absent(Status.NOT_FOUND, "RESULT_NOT_FOUND"); }
    private Observation inconsistent() { return Observation.absent(Status.UNAVAILABLE, "REPORT_RESULT_INCONSISTENT"); }
}
