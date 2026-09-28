package cn.iocoder.yudao.module.pms.acceptance.api.deliverable;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableArchiveFactApi.ProjectDeliverableArchiveFact;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 项目交付件归档事实只读实现：统一交付件平台（plt_delivery_*）提交台账与材料的投影，不提供写入口。
 * 类别判定由 ACC Owner 完成（验收活动类型 / 满意度来源），消费方只做展示。
 */
@Service
@RequiredArgsConstructor
public class ProjectDeliverableArchiveFactApiImpl implements ProjectDeliverableArchiveFactApi {

    private static final String SOURCE_REPORT = "AcceptanceReportVersion";
    private static final String SOURCE_SATISFACTION = "SatisfactionResult";
    private static final String SOURCE_MANUAL = "ProjectDeliverableSubmission";
    private static final String KEY_REPORT_PREFIX = "report:";
    private static final String KEY_SATISFACTION_PREFIX = "satisfaction-result:";

    private final PlatformDeliveryRequirementApi platform;
    private final AcceptanceReportVersionMapper reportVersionMapper;
    private final AcceptanceActivityMapper acceptanceActivityMapper;

    @Override
    public List<ProjectDeliverableArchiveFact> listByProject(Long tenantId, Long projectId) {
        Long currentTenantId = TenantContextHolder.getRequiredTenantId();
        if (tenantId == null || projectId == null || projectId <= 0 || !Objects.equals(tenantId, currentTenantId)) {
            throw new IllegalArgumentException("invalid project deliverable archive fact query");
        }
        return platform.listByProject(projectId).stream().map(this::toFact).toList();
    }

    private ProjectDeliverableArchiveFact toFact(TemplateFrozenView deliverable) {
        var submission = platform.findCurrentSubmission(deliverable.id()).orElse(null);
        if (submission == null) {
            return new ProjectDeliverableArchiveFact(deliverable.id(), deliverable.deliverableCode(),
                    deliverable.name(), deliverable.stageCode(), deliverable.required(),
                    null, null, null, null, null, null);
        }
        SourceRef source = resolveSource(submission);
        List<TemplateFrozenMaterialView> materials = submission.materialIds() == null
                || submission.materialIds().isEmpty()
                ? List.of()
                : platform.listMaterials(deliverable.id()).stream()
                .filter(material -> submission.materialIds().contains(material.id())).toList();
        return new ProjectDeliverableArchiveFact(deliverable.id(), deliverable.deliverableCode(),
                deliverable.name(), deliverable.stageCode(), deliverable.required(),
                source.objectType(), source.category(), source.objectId(), source.version(),
                "WITHDRAWN".equals(submission.status()) ? "REVOKED" : submission.status(),
                archiveStatus(materials));
    }

    /** 投影提交按 requestKey 反解来源身份；手工/归集/成果提交以提交台账为来源对象。 */
    private SourceRef resolveSource(TemplateFrozenSubmissionView submission) {
        String requestKey = submission.requestKey() == null ? "" : submission.requestKey();
        if (requestKey.startsWith(KEY_REPORT_PREFIX)) {
            Long reportVersionId = parseSuffix(requestKey.substring(KEY_REPORT_PREFIX.length()));
            return reportVersionId == null ? SourceRef.manual(submission)
                    : new SourceRef(SOURCE_REPORT, resolveAcceptanceCategory(reportVersionId),
                    reportVersionId, reportVersionNo(reportVersionId));
        }
        if (requestKey.startsWith(KEY_SATISFACTION_PREFIX)) {
            String[] parts = requestKey.substring(KEY_SATISFACTION_PREFIX.length()).split(":");
            Long resultId = parts.length > 0 ? parseSuffix(parts[0]) : null;
            Long resultVersion = parts.length > 1 ? parseSuffix(parts[1]) : null;
            Integer resultVersionNo = resultVersion == null ? null : resultVersion.intValue();
            return resultId == null ? SourceRef.manual(submission)
                    : new SourceRef(SOURCE_SATISFACTION, "SATISFACTION", resultId, resultVersionNo);
        }
        return SourceRef.manual(submission);
    }

    private Long parseSuffix(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private Integer reportVersionNo(Long reportVersionId) {
        AcceptanceReportVersionDO version = reportVersionMapper.selectById(reportVersionId);
        return version == null ? null : version.getReportVersionNo();
    }

    private String resolveAcceptanceCategory(Long reportVersionId) {
        AcceptanceReportVersionDO version = reportVersionMapper.selectById(reportVersionId);
        if (version == null || version.getAcceptanceId() == null) {
            return null;
        }
        AcceptanceActivityDO activity = acceptanceActivityMapper.selectById(version.getAcceptanceId());
        return activity == null ? null : activity.getAcceptanceType();
    }

    /** 材料级归档状态聚合：全部 ARCHIVED → ARCHIVED；任一 INVALID → INVALID；否则仍待补偿。 */
    private String archiveStatus(List<TemplateFrozenMaterialView> materials) {
        if (materials.isEmpty()) return null;
        if (materials.stream().allMatch(material ->
                PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED.equals(material.archiveStatus()))) {
            return PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED;
        }
        if (materials.stream().anyMatch(material ->
                PlatformDeliveryRequirementApi.ARCHIVE_INVALID.equals(material.archiveStatus()))) {
            return PlatformDeliveryRequirementApi.ARCHIVE_INVALID;
        }
        return PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION;
    }

    private record SourceRef(String objectType, String category, Long objectId, Integer version) {
        private static SourceRef manual(TemplateFrozenSubmissionView submission) {
            return new SourceRef(SOURCE_MANUAL, null, submission.id(), null);
        }
    }
}
