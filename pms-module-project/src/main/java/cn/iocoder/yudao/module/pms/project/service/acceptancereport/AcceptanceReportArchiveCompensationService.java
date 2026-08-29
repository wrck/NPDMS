package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.ArchiveFileReferenceSetsCommand;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceIdLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AcceptanceReportArchiveCompensationService {

    private final AccProjectDeliverableMapper deliverableMapper;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final ProjectDeliverableSourceAttachmentMapper sourceAttachmentMapper;
    private final AcceptanceReportVersionMapper reportMapper;
    private final FileArtifactApi fileArtifactApi;

    @Transactional(rollbackFor = Exception.class)
    public void archive(Long tenantId, Long sourceVersionId) {
        ProjectDeliverableSourceVersionDO snapshot = sourceMapper.selectById(sourceVersionId);
        if (snapshot == null || !Objects.equals(snapshot.getTenantId(), tenantId)) {
            throw new IllegalStateException("archive source unavailable");
        }
        AccProjectDeliverableDO deliverable = deliverableMapper.selectByIdForUpdate(
                new ProjectDeliverableIdLockQuery(tenantId, snapshot.getDeliverableId()));
        ProjectDeliverableSourceVersionDO source = sourceMapper.selectByIdForUpdate(
                new DeliverableSourceIdLockQuery(tenantId, sourceVersionId));
        if (deliverable == null || source == null || !Objects.equals(source.getDeliverableId(), deliverable.getId())) {
            throw new IllegalStateException("archive source identity conflict");
        }
        if ("ARCHIVED".equals(source.getArchiveStatus())) return;
        if (!"CURRENT".equals(source.getRelationStatus()) || !"PENDING_COMPENSATION".equals(source.getArchiveStatus())) {
            throw new IllegalStateException("archive source state conflict");
        }
        AcceptanceReportVersionDO report = reportMapper.selectById(source.getSourceObjectId());
        if (report == null || !Objects.equals(report.getTenantId(), tenantId) || report.getPublisherUserId() == null) {
            throw new IllegalStateException("archive publisher unavailable");
        }
        List<ProjectDeliverableSourceAttachmentDO> rows = sourceAttachmentMapper.selectBySourceVersion(source.getId());
        if (rows.isEmpty()) throw new IllegalStateException("archive attachments missing");
        List<FileArtifactVersionFact> facts = rows.stream().map(this::toFact).toList();
        Long scopeVersion = facts.getFirst().scopeVersion();
        if (facts.stream().anyMatch(fact -> !Objects.equals(scopeVersion, fact.scopeVersion()))) {
            throw new IllegalStateException("archive attachment scope conflict");
        }
        FileReferenceSetKey attachmentKey = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION",
                String.valueOf(report.getId()), "ACCEPTANCE_REPORT_ATTACHMENT");
        FileReferenceSetKey archiveKey = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION",
                String.valueOf(report.getId()), "ACCEPTANCE_REPORT_ARCHIVE");
        fileArtifactApi.archiveReferenceSets(new ArchiveFileReferenceSetsCommand(
                "ACC-ARCHIVE:" + source.getId(), "ACC-ARCHIVE:" + source.getId(),
                "ACC-REPORT:" + report.getId(), report.getPublisherUserId(),
                attachmentKey, archiveKey, scopeVersion, facts));
        source.setArchiveStatus("ARCHIVED");
        source.setArchiveFailureCode(null);
        source.setArchiveTime(LocalDateTime.now());
        source.setUpdater(String.valueOf(report.getPublisherUserId()));
        if (sourceMapper.updateById(source) != 1) throw new IllegalStateException("archive projection update failed");
        if (Objects.equals(deliverable.getCurrentSourceVersionId(), source.getId())) {
            deliverable.setArchiveStatus("ARCHIVED");
            deliverable.setVersion(deliverable.getVersion() + 1);
            deliverable.setUpdater(String.valueOf(report.getPublisherUserId()));
            if (deliverableMapper.updateById(deliverable) != 1) {
                throw new IllegalStateException("deliverable archive update failed");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(Long tenantId, Long sourceVersionId, String failureCode) {
        ProjectDeliverableSourceVersionDO snapshot = sourceMapper.selectById(sourceVersionId);
        if (snapshot == null || !Objects.equals(snapshot.getTenantId(), tenantId)) return;
        deliverableMapper.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(tenantId, snapshot.getDeliverableId()));
        ProjectDeliverableSourceVersionDO source = sourceMapper.selectByIdForUpdate(
                new DeliverableSourceIdLockQuery(tenantId, sourceVersionId));
        if (source == null || !"PENDING_COMPENSATION".equals(source.getArchiveStatus())) return;
        source.setArchiveFailureCode(failureCode);
        source.setArchiveRetryCount(source.getArchiveRetryCount() == null ? 1 : source.getArchiveRetryCount() + 1);
        if (sourceMapper.updateById(source) != 1) throw new IllegalStateException("archive failure watermark update failed");
    }

    private FileArtifactVersionFact toFact(ProjectDeliverableSourceAttachmentDO row) {
        return new FileArtifactVersionFact(row.getFileArtifactId(), row.getFileVersionNo(), row.getReferenceKey(),
                null, null, null, null, row.getFileHash(), "AVAILABLE", "ACTIVE",
                new FileFactVersion(row.getArtifactVersion(), row.getReferenceVersion(), row.getAvailabilityVersion()),
                row.getScopeVersion());
    }
}
