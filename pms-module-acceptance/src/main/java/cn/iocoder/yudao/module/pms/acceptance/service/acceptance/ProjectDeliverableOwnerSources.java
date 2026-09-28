package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceActivityScopeQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionCollectionTaskMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.SatisfactionResultMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.satisfaction.query.SatisfactionTaskScopeQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 报告/满意度投影的来源事实重验（P06R I2）：投影提交以 requestPayloadJson 携带投影身份，
 * 本判定只做来源业务事实核对（绑定关系、版本指针、现行状态）与材料文件逐条锁定重验——
 * 文件按 inspectDocument 返回的自身身份构造锁查询，兼容新旧锚（不可变历史不重挂）。
 */
@Service
@RequiredArgsConstructor
public class ProjectDeliverableOwnerSources {

    private final PlatformDeliveryRequirementApi platform;
    private final AcceptanceActivityMapper activities;
    private final AcceptanceReportVersionMapper reports;
    private final SatisfactionCollectionTaskMapper tasks;
    private final SatisfactionResultMapper results;
    private final FileEvidenceApi files;

    public record Evidence(boolean valid, String reason, int fileCount, List<FileEvidenceApi.Fact> files) { }

    /** 绑定判定：acc_acceptance / acc_satisfaction_collection_task 的 deliverable_id 指向要求实例 ID。 */
    public String ownerType(PlatformDeliveryRequirementApi.TemplateFrozenView view) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (activities.selectByProjectScope(new AcceptanceActivityScopeQuery(tenantId, Set.of(view.projectId())))
                .stream().anyMatch(activity -> Objects.equals(activity.getDeliverableId(), view.id()))) {
            return "ACCEPTANCE_REPORT";
        }
        if (tasks.selectByScope(new SatisfactionTaskScopeQuery(tenantId, Set.of(view.projectId()), null))
                .stream().anyMatch(task -> Objects.equals(task.getDeliverableId(), view.id()))) {
            return "SATISFACTION_RESULT";
        }
        return null;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Evidence revalidate(PlatformDeliveryRequirementApi.TemplateFrozenView view,
                               PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView submission) {
        if (!Objects.equals(submission.requirementId(), view.id())) {
            return invalid("DELIVERABLE_SOURCE_INVALID");
        }
        var payload = JsonUtils.parseTree(submission.requestPayloadJson() == null ? "{}" : submission.requestPayloadJson());
        String kind = payload.path("projectionKind").asText("");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if ("ACCEPTANCE_REPORT".equals(kind)) {
            Long reportVersionId = payload.path("reportVersionId").asLong(0);
            var observed = reports.selectById(reportVersionId);
            if (observed == null || !Objects.equals(observed.getTenantId(), tenantId)) {
                return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            }
            var activity = activities.selectByIdForUpdate(new AcceptanceActivityIdLockQuery(tenantId, observed.getAcceptanceId()));
            var report = reports.selectByIdForUpdate(new AcceptanceReportIdLockQuery(tenantId, observed.getAcceptanceId(), observed.getId()));
            if (activity == null || report == null || Boolean.TRUE.equals(activity.getDeleted()) || Boolean.TRUE.equals(report.getDeleted())
                    || !Objects.equals(activity.getProjectId(), view.projectId())
                    || !Objects.equals(activity.getDeliverableId(), view.id())
                    || !Objects.equals(activity.getCurrentReportVersionId(), reportVersionId)
                    || !Objects.equals(report.getReportVersionNo(), payload.path("reportVersionNo").asInt(0))
                    || !"EFFECTIVE".equals(report.getReportStatus())
                    || report.getEffectiveTo() != null) {
                return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            }
        } else if ("SATISFACTION_RESULT".equals(kind)) {
            Long resultId = payload.path("resultId").asLong(0);
            var observed = results.selectById(resultId);
            if (observed == null || !Objects.equals(observed.getTenantId(), tenantId)) {
                return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            }
            var task = tasks.selectByIdForUpdate(tenantId, observed.getCollectionTaskId());
            var result = results.selectByIdForUpdate(tenantId, observed.getId());
            if (task == null || result == null || Boolean.TRUE.equals(task.getDeleted())
                    || !Objects.equals(task.getProjectId(), view.projectId())
                    || !Objects.equals(task.getDeliverableId(), view.id())
                    || !Objects.equals(task.getResultId(), result.getId())
                    || !Objects.equals(result.getResultVersion(), payload.path("resultVersion").asInt(0))
                    || !"EFFECTIVE".equals(result.getResultStatus()) || !Boolean.TRUE.equals(result.getPassed())
                    || result.getEffectiveTo() != null) {
                return invalid("DELIVERABLE_BUSINESS_RESULT_INVALID");
            }
        } else {
            return invalid("DELIVERABLE_SOURCE_UNSUPPORTED");
        }
        return revalidateMaterials(view, submission, tenantId);
    }

    private Evidence revalidateMaterials(PlatformDeliveryRequirementApi.TemplateFrozenView view,
                                         PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView submission,
                                         Long tenantId) {
        var evidence = new ArrayList<FileEvidenceApi.Fact>();
        int count = 0;
        for (var material : platform.listMaterials(view.id())) {
            if (!submission.materialIds().contains(material.id())) {
                continue;
            }
            if (!PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE.equals(material.materialKind())) {
                continue;
            }
            count++;
            var document = files.inspectDocument(tenantId, material.fileReferenceId());
            if (document == null || !document.available()
                    || !document.sha256().equals(material.fileSha256())) {
                return new Evidence(false, "DELIVERABLE_FILE_UNAVAILABLE", 0, List.copyOf(evidence));
            }
            var fact = files.lockAndRevalidate(new FileEvidenceApi.Query(tenantId, document.artifactId(),
                    document.versionNo(), document.ownerContext(), document.objectType(), document.objectId(),
                    document.purposeCode(), document.referenceKey(), document.sha256()));
            evidence.add(fact);
            if (!fact.valid()) {
                return new Evidence(false, fact.reason(), 0, List.copyOf(evidence));
            }
        }
        return new Evidence(true, "DELIVERABLE_SOURCE_VALID", count, List.copyOf(evidence));
    }

    private Evidence invalid(String reason) {
        return new Evidence(false, reason, 0, List.of());
    }
}
