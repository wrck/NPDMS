package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionTaskScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Reuses ACC's existing report/result projections without replacing their source-version chains. */
@Service @RequiredArgsConstructor
public class ProjectDeliverableOwnerSources {
    private final AcceptanceActivityMapper activities;
    private final AcceptanceReportVersionMapper reports;
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionResultMapper results;
    private final ProjectDeliverableSourceAttachmentMapper attachments;
    private final FileEvidenceApi files;

    public record Evidence(boolean valid, String reason, int fileCount, List<FileEvidenceApi.Fact> files) { }

    public String ownerType(AccProjectDeliverableDO row) {
        if (activities.selectByProjectScope(new AcceptanceActivityScopeQuery(row.getTenantId(), Set.of(row.getProjectId())))
                .stream().anyMatch(activity -> Objects.equals(activity.getDeliverableId(), row.getId()))) return "ACCEPTANCE_REPORT";
        if (tasks.selectByScope(new SatisfactionTaskScopeQuery(row.getTenantId(), Set.of(row.getProjectId()), null))
                .stream().anyMatch(task -> Objects.equals(task.getDeliverableId(), row.getId()))) return "SATISFACTION_RESULT";
        return null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Evidence revalidate(AccProjectDeliverableDO row, ProjectDeliverableSourceVersionDO source) {
        if (!Objects.equals(source.getTenantId(), row.getTenantId()) || !Objects.equals(source.getDeliverableId(), row.getId())
                || !"CURRENT".equals(source.getRelationStatus())) return invalid("DELIVERABLE_SOURCE_INVALID");
        String type, purpose;
        if ("AcceptanceReportVersion".equals(source.getSourceObjectType())) {
            var observed = reports.selectById(source.getSourceObjectId());
            if (observed == null || !Objects.equals(observed.getTenantId(), row.getTenantId())) return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            var activity = activities.selectByIdForUpdate(new AcceptanceActivityIdLockQuery(row.getTenantId(), observed.getAcceptanceId()));
            var report = reports.selectByIdForUpdate(new AcceptanceReportIdLockQuery(row.getTenantId(), observed.getAcceptanceId(), observed.getId()));
            if (activity == null || report == null || Boolean.TRUE.equals(activity.getDeleted()) || Boolean.TRUE.equals(report.getDeleted())
                    || !Objects.equals(activity.getProjectId(), row.getProjectId()) || !Objects.equals(activity.getDeliverableId(), row.getId())
                    || !Objects.equals(activity.getCurrentReportVersionId(), source.getSourceObjectId())
                    || !Objects.equals(report.getReportVersionNo(), source.getSourceVersion()) || !"EFFECTIVE".equals(report.getReportStatus())
                    || report.getEffectiveTo() != null) return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            type = "ACCEPTANCE_REPORT_VERSION"; purpose = "ACCEPTANCE_REPORT_ATTACHMENT";
        } else if ("SatisfactionResult".equals(source.getSourceObjectType())) {
            var observed = results.selectById(source.getSourceObjectId());
            if (observed == null || !Objects.equals(observed.getTenantId(), row.getTenantId())) return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            var task = tasks.selectByIdForUpdate(row.getTenantId(), observed.getCollectionTaskId());
            var result = results.selectByIdForUpdate(row.getTenantId(), observed.getId());
            if (task == null || result == null || Boolean.TRUE.equals(task.getDeleted())
                    || !Objects.equals(task.getProjectId(), row.getProjectId()) || !Objects.equals(task.getDeliverableId(), row.getId())
                    || !Objects.equals(task.getResultId(), result.getId()) || !Objects.equals(result.getResultVersion(), source.getSourceVersion())
                    || !"EFFECTIVE".equals(result.getResultStatus()) || !Boolean.TRUE.equals(result.getPassed())
                    || result.getEffectiveTo() != null) return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            type = "SATISFACTION_RESULT"; purpose = "SATISFACTION_RESULT_DOCUMENT";
        } else return invalid("DELIVERABLE_SOURCE_UNSUPPORTED");
        var evidence = new ArrayList<FileEvidenceApi.Fact>();
        var sourceFiles = attachments.selectBySourceVersion(source.getId()).stream()
                .sorted(Comparator.comparing(file -> file.getFileArtifactId())).toList();
        for (var file : sourceFiles) {
            var fact = files.lockAndRevalidate(new FileEvidenceApi.Query(row.getTenantId(), file.getFileArtifactId(), file.getFileVersionNo(),
                    "ACC", type, source.getSourceObjectId().toString(), purpose, file.getReferenceKey(), file.getFileHash()));
            evidence.add(fact);
            if (!fact.valid()) return new Evidence(false, fact.reason(), 0, List.copyOf(evidence));
        }
        return new Evidence(true, "DELIVERABLE_SOURCE_VALID", sourceFiles.size(), List.copyOf(evidence));
    }

    private Evidence invalid(String reason) { return new Evidence(false, reason, 0, List.of()); }
}
