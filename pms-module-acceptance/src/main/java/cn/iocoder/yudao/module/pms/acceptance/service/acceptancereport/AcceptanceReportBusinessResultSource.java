package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import lombok.RequiredArgsConstructor;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChangeSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import org.springframework.stereotype.Component;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.ReportResultInventoryQuery;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/** A published report is a report result, never an assertion that its acceptance activity completed. */
@Component
@RequiredArgsConstructor
public class AcceptanceReportBusinessResultSource implements BusinessResultChangeSource, BusinessResultInventorySource {
    public static final Type TYPE = new Type("ACC", "ACCEPTANCE", "REPORT_VERSION_PUBLISHED");
    private static final Descriptor DESCRIPTOR = new Descriptor(TYPE, true, true, true);
    private final AcceptanceActivityMapper activities;
    private final AcceptanceReportVersionMapper reports;
    private final cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper attachments;
    private final cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi fileEvidence;

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
        var ids = reports.selectResultInventory(new ReportResultInventoryQuery(query.tenantId(), query.projectId(),
                BusinessResultInventorySource.nativeObjects(query), BusinessResultSource.nativeId(query.after()), query.limit() + 1, query.historical()));
        return BusinessResultInventorySource.nativePage(query, ids, id -> inspect(new Query(query.tenantId(), query.projectId(), TYPE, null, id)));
    }

    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (event == null || !TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType()))
            throw new IllegalArgumentException("RESULT_EVENT_TYPE_INVALID");
        if (event.revisionId() == null) throw new IllegalArgumentException("REPORT_RESULT_EVENT_IDENTITY_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, event.objectId(), event.revisionId());
    }

    @Override public Observation inspect(Query query) {
        return inspect(query, false);
    }

    @Override @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Observation lockAndInspect(Query query) { return inspect(query, true); }

    private Observation inspect(Query query, boolean lock) {
        if (query == null || !TYPE.equals(query.type())
                || !Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
        Long objectId = BusinessResultSource.nativeId(query.objectId());
        Long resultId = BusinessResultSource.nativeId(query.resultId());
        if (lock && (objectId == null || resultId == null)) throw new IllegalArgumentException("RESULT_EXACT_IDENTITY_REQUIRED");
        var lockedActivity = lock ? activities.selectByIdForUpdate(
                new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery(query.tenantId(), objectId)) : null;
        var report = resultId == null ? null : lock ? reports.selectByIdForUpdate(
                new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery(query.tenantId(), objectId, resultId)) : reports.selectById(resultId);
        if (resultId != null) {
            if (report == null || Boolean.TRUE.equals(report.getDeleted())) return missing();
            if (!Objects.equals(query.tenantId(), report.getTenantId()) || !Objects.equals(resultId, report.getId())
                    || report.getAcceptanceId() == null || report.getAcceptanceId() <= 0
                    || objectId != null && !objectId.equals(report.getAcceptanceId()))
                throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
            objectId = report.getAcceptanceId();
        }
        var activity = lock ? lockedActivity : activities.selectById(objectId);
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
        if (lock && current && !validCurrentFiles(query.tenantId(), report))
            return Observation.absent(Status.UNAVAILABLE, "REPORT_FILE_EVIDENCE_INVALID");
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, activity.getId().toString(),
                report.getId().toString(), report.getReportVersionNo().toString(), activity.getVersion().toString(),
                validity, report.getEffectiveFrom()));
    }

    private boolean validCurrentFiles(Long tenant, cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO report) {
        if (report.getAcceptanceTime() == null || report.getConclusionCode() == null || report.getConclusionCode().isBlank()
                || report.getAcceptorName() == null || report.getAcceptorName().isBlank()) return false;
        var rows = attachments.selectByReportVersion(report.getId());
        if (rows.isEmpty()) return false;
        for (var row : rows) {
            if (!Objects.equals(row.getTenantId(), tenant) || !Objects.equals(row.getReportVersionId(), report.getId())
                    || Boolean.TRUE.equals(row.getDeleted()) || row.getFileHash() == null) return false;
            var fact = fileEvidence.lockAndRevalidate(new cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi.Query(
                    tenant, row.getFileArtifactId(), row.getFileVersionNo(), "ACC", "ACCEPTANCE_REPORT_VERSION", report.getId().toString(),
                    "ACCEPTANCE_REPORT_ATTACHMENT", row.getReferenceKey(), row.getFileHash()));
            if (fact == null || !fact.valid() || !Objects.equals(fact.artifactVersion(), row.getArtifactVersion())
                    || !Objects.equals(fact.availabilityVersion(), row.getAvailabilityVersion())
                    || !Objects.equals(fact.referenceVersion(), row.getReferenceVersion())) return false;
        }
        return true;
    }

    private Observation missing() { return Observation.absent(Status.NOT_FOUND, "RESULT_NOT_FOUND"); }
    private Observation inconsistent() { return Observation.absent(Status.UNAVAILABLE, "REPORT_RESULT_INCONSISTENT"); }
}
