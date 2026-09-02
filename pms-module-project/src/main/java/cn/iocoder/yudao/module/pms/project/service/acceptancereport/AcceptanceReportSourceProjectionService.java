package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdentityLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableCurrentSourceLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceIdentityQuery;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AcceptanceReportSourceProjectionService {

    private final AccProjectDeliverableMapper deliverableMapper;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final ProjectDeliverableSourceAttachmentMapper sourceAttachmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public void project(AcceptanceReportVersionChangedMessage event) {
        validate(event);
        String deliverableCode = "PRELIMINARY".equals(event.reportType())
                ? "D-INITIAL-REPORT" : "D-FINAL-REPORT";
        AccProjectDeliverableDO deliverable = deliverableMapper.selectByProjectAndCodeForUpdate(
                new ProjectDeliverableIdentityLockQuery(event.tenantId(), event.projectId(), deliverableCode));
        if (deliverable == null) throw new IllegalStateException("acceptance deliverable root unavailable");
        if ("REVOKED".equals(event.changeType())) {
            revoke(event, deliverable);
            return;
        }
        upsertCurrent(event, deliverable);
    }

    private void upsertCurrent(AcceptanceReportVersionChangedMessage event, AccProjectDeliverableDO deliverable) {
        ProjectDeliverableSourceVersionDO existing = sourceMapper.selectIdentityForUpdate(
                new DeliverableSourceIdentityQuery(event.tenantId(), deliverable.getId(),
                        event.currentReportVersionId(), event.reportVersionNo()));
        if (existing != null) {
            if (!"CURRENT".equals(existing.getRelationStatus())
                    || !Objects.equals(deliverable.getCurrentSourceVersionId(), existing.getId())) {
                throw new IllegalStateException("acceptance source replay conflict");
            }
            return;
        }
        ProjectDeliverableSourceVersionDO current = sourceMapper.selectCurrentForUpdate(
                new DeliverableCurrentSourceLockQuery(event.tenantId(), deliverable.getId()));
        if (current != null) {
            if (!"REPLACED".equals(event.changeType())
                    || !Objects.equals(current.getSourceObjectId(), event.previousReportVersionId())) {
                throw new IllegalStateException("acceptance source current conflict");
            }
            current.setRelationStatus("SUPERSEDED");
            current.setUpdater(String.valueOf(event.publisherActorUserId()));
            if (sourceMapper.updateById(current) != 1) throw new IllegalStateException("source update failed");
        } else if (!"EFFECTIVE".equals(event.changeType())) {
            throw new IllegalStateException("acceptance source previous version missing");
        }
        ProjectDeliverableSourceVersionDO source = new ProjectDeliverableSourceVersionDO();
        source.setId(IdWorker.getId());
        source.setDeliverableId(deliverable.getId());
        source.setSourceRequirementId("ACC-03@V1");
        source.setSourceObjectType("AcceptanceReportVersion");
        source.setSourceObjectId(event.currentReportVersionId());
        source.setSourceVersion(event.reportVersionNo());
        source.setRelationStatus("CURRENT");
        source.setArchiveStatus("PENDING_COMPENSATION");
        source.setArchiveRetryCount(0);
        source.setCreator(String.valueOf(event.publisherActorUserId()));
        source.setUpdater(String.valueOf(event.publisherActorUserId()));
        source.setTenantId(event.tenantId());
        if (sourceMapper.insert(source) != 1) throw new IllegalStateException("source insert failed");
        insertAttachments(event, source.getId());
        deliverable.setCurrentSourceVersionId(source.getId());
        deliverable.setArchiveStatus("PENDING_COMPENSATION");
        deliverable.setVersion(deliverable.getVersion() + 1);
        deliverable.setUpdater(String.valueOf(event.publisherActorUserId()));
        if (deliverableMapper.updateById(deliverable) != 1) throw new IllegalStateException("deliverable update failed");
    }

    private void revoke(AcceptanceReportVersionChangedMessage event, AccProjectDeliverableDO deliverable) {
        ProjectDeliverableSourceVersionDO current = sourceMapper.selectCurrentForUpdate(
                new DeliverableCurrentSourceLockQuery(event.tenantId(), deliverable.getId()));
        if (current == null) {
            if (deliverable.getCurrentSourceVersionId() == null && "INVALID".equals(deliverable.getArchiveStatus())) return;
            throw new IllegalStateException("acceptance source current missing");
        }
        if (!Objects.equals(current.getSourceObjectId(), event.previousReportVersionId())) {
            throw new IllegalStateException("acceptance source revoke conflict");
        }
        current.setRelationStatus("REVOKED");
        current.setArchiveStatus("INVALID");
        current.setUpdater(String.valueOf(event.publisherActorUserId()));
        if (sourceMapper.updateById(current) != 1) throw new IllegalStateException("source revoke failed");
        deliverable.setCurrentSourceVersionId(null);
        deliverable.setArchiveStatus("INVALID");
        deliverable.setVersion(deliverable.getVersion() + 1);
        deliverable.setUpdater(String.valueOf(event.publisherActorUserId()));
        if (deliverableMapper.updateById(deliverable) != 1) throw new IllegalStateException("deliverable revoke failed");
    }

    private void insertAttachments(AcceptanceReportVersionChangedMessage event, Long sourceId) {
        int sequence = 1;
        for (FileArtifactVersionFact fact : event.attachments()) {
            ProjectDeliverableSourceAttachmentDO row = new ProjectDeliverableSourceAttachmentDO();
            row.setId(IdWorker.getId());
            row.setDeliverableSourceVersionId(sourceId);
            row.setAttachmentSequence(sequence++);
            row.setFileArtifactId(fact.artifactId());
            row.setFileVersionNo(fact.versionNo());
            row.setReferenceKey(fact.referenceKey());
            row.setArtifactVersion(fact.fileFactVersion().artifactVersion());
            row.setReferenceVersion(fact.fileFactVersion().referenceVersion());
            row.setAvailabilityVersion(fact.fileFactVersion().availabilityVersion());
            row.setScopeVersion(fact.scopeVersion());
            row.setFileHash(fact.sha256());
            row.setCreator(String.valueOf(event.publisherActorUserId()));
            row.setUpdater(String.valueOf(event.publisherActorUserId()));
            row.setTenantId(event.tenantId());
            if (sourceAttachmentMapper.insert(row) != 1) throw new IllegalStateException("source attachment insert failed");
        }
    }

    private void validate(AcceptanceReportVersionChangedMessage event) {
        List<String> changes = List.of("EFFECTIVE", "REPLACED", "REVOKED");
        if (event == null || event.tenantId() == null || event.acceptanceId() == null || event.projectId() == null
                || !List.of("PRELIMINARY", "FINAL").contains(event.reportType())
                || !changes.contains(event.changeType()) || event.publisherActorUserId() == null
                || event.publisherActorUserId() <= 0
                || ("REVOKED".equals(event.changeType())
                ? event.currentReportVersionId() != null || event.previousReportVersionId() == null
                : event.currentReportVersionId() == null || event.reportVersionNo() == null
                || event.reportVersionNo() <= 0 || event.attachments().isEmpty()
                || event.attachments().stream().anyMatch(this::invalidAttachment))) {
            throw new IllegalArgumentException("invalid acceptance report version event");
        }
    }

    private boolean invalidAttachment(FileArtifactVersionFact fact) {
        return fact == null || fact.artifactId() == null || fact.artifactId() <= 0
                || fact.versionNo() == null || fact.versionNo() <= 0
                || fact.referenceKey() == null || fact.referenceKey().isBlank()
                || fact.fileFactVersion() == null || fact.scopeVersion() == null
                || fact.sha256() == null || fact.sha256().length() != 64;
    }
}
