package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 报告投影材料归档补偿（P06R I2）：材料行是补偿主体，投影来源经 材料 → 提交台账 → report:{版本ID}
 * 反查；文件事实按工件版本现场重建（业务附件冻结元组的一致性由任务绑定侧 matchesAttachments 守护）。
 * 撤销置 INVALID 的材料不再进入补偿（仅 PENDING_COMPENSATION 推进）。
 */
@Service
@RequiredArgsConstructor
public class AcceptanceReportArchiveCompensationService {

    private final PlatformDeliveryRequirementApi platform;
    private final AcceptanceReportVersionMapper reportMapper;
    private final FileEvidenceApi fileEvidence;
    private final FileArtifactApi fileArtifactApi;

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long tenantId, Long materialId) {
        List<TemplateFrozenMaterialView> locked = platform.lockMaterials(List.of(materialId));
        if (locked.isEmpty()) throw new IllegalStateException("archive source unavailable");
        TemplateFrozenMaterialView material = locked.getFirst();
        if (PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED.equals(material.archiveStatus())) return;
        if (!PlatformDeliveryRequirementApi.ARCHIVE_PENDING_COMPENSATION.equals(material.archiveStatus())
                || !PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE.equals(material.materialKind())) {
            throw new IllegalStateException("archive source state conflict");
        }
        TemplateFrozenSubmissionView submission = requireReportProjection(material);
        long reportId = Long.parseLong(
                AcceptanceReportSourceProjectionService.projectionVersionId(submission.requestKey()));
        AcceptanceReportVersionDO report = reportMapper.selectById(reportId);
        if (report == null || !Objects.equals(report.getTenantId(), tenantId) || report.getPublisherUserId() == null) {
            throw new IllegalStateException("archive publisher unavailable");
        }
        List<TemplateFrozenMaterialView> materials = platform.lockMaterials(submission.materialIds());
        if (materials.isEmpty()) throw new IllegalStateException("archive attachments missing");
        List<FileArtifactVersionFact> facts = materials.stream().map(this::toFact).toList();
        Long scopeVersion = facts.getFirst().scopeVersion();
        if (facts.stream().anyMatch(fact -> !Objects.equals(scopeVersion, fact.scopeVersion()))) {
            throw new IllegalStateException("archive attachment scope conflict");
        }
        FileReferenceSetKey attachmentKey = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION",
                String.valueOf(report.getId()), "ACCEPTANCE_REPORT_ATTACHMENT");
        FileReferenceSetKey archiveKey = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION",
                String.valueOf(report.getId()), "ACCEPTANCE_REPORT_ARCHIVE");
        fileArtifactApi.archiveReferenceSets(new ArchiveFileReferenceSetsCommand(
                "ACC-ARCHIVE:" + submission.id(), "ACC-ARCHIVE:" + submission.id(),
                "ACC-REPORT:" + report.getId(), report.getPublisherUserId(),
                attachmentKey, archiveKey, scopeVersion, facts));
        LocalDateTime now = LocalDateTime.now();
        String actor = String.valueOf(report.getPublisherUserId());
        for (TemplateFrozenMaterialView row : materials) {
            platform.markMaterialArchiveState(row.id(), PlatformDeliveryRequirementApi.ARCHIVE_ARCHIVED, null, now, actor);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long materialId, String failureCode) {
        platform.bumpMaterialArchiveRetry(materialId, failureCode);
    }

    private TemplateFrozenSubmissionView requireReportProjection(TemplateFrozenMaterialView material) {
        Long submissionId = platform.findSubmissionIdByMaterial(material.id())
                .orElseThrow(() -> new IllegalStateException("archive source identity conflict"));
        TemplateFrozenSubmissionView submission = platform.findSubmissionById(submissionId)
                .orElseThrow(() -> new IllegalStateException("archive source identity conflict"));
        if (!PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(submission.sourceType())
                || AcceptanceReportSourceProjectionService.projectionVersionId(submission.requestKey()).isBlank()) {
            throw new IllegalStateException("archive source identity conflict");
        }
        return submission;
    }

    private FileArtifactVersionFact toFact(TemplateFrozenMaterialView material) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FileEvidenceApi.Document document = fileEvidence.inspectDocumentByArtifact(
                tenantId, material.fileArtifactId(), material.fileVersionNo());
        if (document == null || !document.available()
                || !Objects.equals(document.sha256(), material.fileSha256())) {
            throw new IllegalStateException("archive material reference unavailable");
        }
        FileArtifactVersionFact fact = fileArtifactApi.inspect(new FileArtifactVersionQuery(
                document.artifactId(), document.versionNo(), document.ownerContext(), document.objectType(),
                document.objectId(), document.purposeCode(), document.referenceKey(), FileActionCodes.REFERENCE));
        if (fact == null || fact.fileFactVersion() == null || fact.scopeVersion() == null
                || !Objects.equals(fact.sha256(), material.fileSha256())) {
            throw new IllegalStateException("archive material reference unavailable");
        }
        return fact;
    }
}
