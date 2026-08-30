package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.module.pms.project.api.satisfaction.SatisfactionResultFactApi;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFact;
import cn.iocoder.yudao.module.pms.project.api.satisfaction.dto.SatisfactionResultFactQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectSatisfactionTaskFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.ProjectDeliverableSourceVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdentityLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableCurrentSourceLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceObjectIdentityQuery;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.event.SatisfactionResultVersionChangedMessage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SatisfactionResultSourceProjectionService {
    private static final String TASK_CODE = "T-SAT-SURVEY";
    private static final String DELIVERABLE_CODE = "D-SAT-REPORT";

    private final ProjectWorkBindingFactApi workBindingFactApi;
    private final SatisfactionResultFactApi resultFactApi;
    private final AccProjectDeliverableMapper deliverableMapper;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final ProjectDeliverableSourceAttachmentMapper attachmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public void project(SatisfactionResultVersionChangedMessage event) {
        validate(event);
        ProjectSatisfactionTaskFact taskFact = workBindingFactApi.lockAndRevalidateSatisfactionTask(
                new ProjectSatisfactionTaskFactQuery(event.projectId(), event.projectTaskId(),
                        event.projectTaskVersion()));
        if (taskFact == null || !Objects.equals(taskFact.projectId(), event.projectId())
                || !Objects.equals(taskFact.projectTaskId(), event.projectTaskId())
                || !Objects.equals(taskFact.projectTaskVersion(), event.projectTaskVersion())
                || !TASK_CODE.equals(taskFact.taskCode()) || !TASK_CODE.equals(event.taskCode())) {
            throw new IllegalStateException("SATISFACTION_SOURCE_PROJECT_TASK_CONFLICT");
        }
        AccProjectDeliverableDO root = deliverableMapper.selectByProjectAndCodeForUpdate(
                new ProjectDeliverableIdentityLockQuery(event.tenantId(), event.projectId(), DELIVERABLE_CODE));
        if (root == null || !TASK_CODE.equals(root.getTaskCode())) {
            throw new IllegalStateException("SATISFACTION_DELIVERABLE_ROOT_UNAVAILABLE");
        }
        if ("INVALIDATED".equals(event.changeType())) {
            invalidate(event, root);
        } else {
            record(event, root);
        }
    }

    private void record(SatisfactionResultVersionChangedMessage event, AccProjectDeliverableDO root) {
        ProjectDeliverableSourceVersionDO existing = find(event, root.getId());
        if (existing != null) return;

        SatisfactionResultFact resultFact = resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(
                event.tenantId(), event.resultId(), event.resultFactVersion()));
        boolean ownerCurrent = exactCurrentResult(event, resultFact);
        ProjectDeliverableSourceVersionDO current = sourceMapper.selectCurrentForUpdate(
                new DeliverableCurrentSourceLockQuery(event.tenantId(), root.getId()));
        boolean becomesCurrent = ownerCurrent && mayBecomeCurrent(event, current);
        if (becomesCurrent && current != null) {
            current.setRelationStatus("SUPERSEDED");
            current.setUpdater(String.valueOf(event.archiveActorUserId()));
            if (sourceMapper.updateById(current) != 1) {
                throw new IllegalStateException("SATISFACTION_SOURCE_SUPERSEDE_CONFLICT");
            }
        }
        ProjectDeliverableSourceVersionDO source = insertSource(event, root.getId(),
                becomesCurrent ? "CURRENT" : "SUPERSEDED");
        insertFiles(event, source.getId());
        if (becomesCurrent) {
            root.setCurrentSourceVersionId(source.getId());
            root.setArchiveStatus("PENDING_COMPENSATION");
            root.setVersion(root.getVersion() + 1);
            root.setUpdater(String.valueOf(event.archiveActorUserId()));
            if (deliverableMapper.updateById(root) != 1) {
                throw new IllegalStateException("SATISFACTION_DELIVERABLE_UPDATE_CONFLICT");
            }
        }
    }

    private void invalidate(SatisfactionResultVersionChangedMessage event, AccProjectDeliverableDO root) {
        SatisfactionResultFact resultFact = resultFactApi.lockAndRevalidate(new SatisfactionResultFactQuery(
                event.tenantId(), event.resultId(), event.resultFactVersion()));
        if (resultFact == null || !"FOUND".equals(resultFact.outcome())
                || !Objects.equals(resultFact.resultId(), event.resultId())
                || !Objects.equals(resultFact.resultVersion(), event.resultVersion())
                || !Objects.equals(resultFact.factVersion(), event.resultFactVersion())
                || !"INVALIDATED".equals(resultFact.resultStatus())) {
            throw new IllegalStateException("SATISFACTION_RESULT_INVALIDATION_FACT_CONFLICT");
        }
        ProjectDeliverableSourceVersionDO source = find(event, root.getId());
        if (source == null) {
            throw new IllegalStateException("SATISFACTION_SOURCE_INVALIDATION_PENDING_RECORDED");
        }
        if (!"REVOKED".equals(source.getRelationStatus())) {
            source.setRelationStatus("REVOKED");
            source.setUpdater(String.valueOf(event.invalidatedByUserId()));
            if (sourceMapper.updateById(source) != 1) {
                throw new IllegalStateException("SATISFACTION_SOURCE_REVOKE_CONFLICT");
            }
        }
        if (Objects.equals(root.getCurrentSourceVersionId(), source.getId())) {
            root.setCurrentSourceVersionId(null);
            root.setArchiveStatus("INVALID");
            root.setVersion(root.getVersion() + 1);
            root.setUpdater(String.valueOf(event.invalidatedByUserId()));
            if (deliverableMapper.updateById(root) != 1) {
                throw new IllegalStateException("SATISFACTION_DELIVERABLE_REVOKE_CONFLICT");
            }
        }
    }

    private boolean exactCurrentResult(SatisfactionResultVersionChangedMessage event, SatisfactionResultFact fact) {
        return fact != null && "FOUND".equals(fact.outcome()) && Objects.equals(fact.resultId(), event.resultId())
                && Objects.equals(fact.resultVersion(), event.resultVersion())
                && Objects.equals(fact.factVersion(), event.resultFactVersion())
                && fact.passed() && "EFFECTIVE".equals(fact.resultStatus());
    }

    private boolean mayBecomeCurrent(SatisfactionResultVersionChangedMessage event,
                                     ProjectDeliverableSourceVersionDO current) {
        if (current == null) return true;
        if (!"SatisfactionResult".equals(current.getSourceObjectType())) {
            throw new IllegalStateException("SATISFACTION_SOURCE_TYPE_CONFLICT");
        }
        if (current.getSourceVersion() > event.resultVersion()) return false;
        if (Objects.equals(current.getSourceVersion(), event.resultVersion())) {
            throw new IllegalStateException("SATISFACTION_SOURCE_VERSION_CONFLICT");
        }
        return true;
    }

    private ProjectDeliverableSourceVersionDO find(SatisfactionResultVersionChangedMessage event, Long rootId) {
        return sourceMapper.selectSourceObjectIdentityForUpdate(new DeliverableSourceObjectIdentityQuery(
                event.tenantId(), rootId, "SatisfactionResult", event.resultId(), event.resultVersion()));
    }

    private ProjectDeliverableSourceVersionDO insertSource(SatisfactionResultVersionChangedMessage event,
                                                           Long rootId, String relationStatus) {
        ProjectDeliverableSourceVersionDO source = new ProjectDeliverableSourceVersionDO();
        source.setId(IdWorker.getId());
        source.setDeliverableId(rootId);
        source.setSourceRequirementId("ACC-04@V1");
        source.setSourceObjectType("SatisfactionResult");
        source.setSourceObjectId(event.resultId());
        source.setSourceVersion(event.resultVersion());
        source.setRelationStatus(relationStatus);
        source.setArchiveStatus("PENDING_COMPENSATION");
        source.setArchiveRetryCount(0);
        source.setTenantId(event.tenantId());
        source.setCreator(String.valueOf(event.archiveActorUserId()));
        source.setUpdater(String.valueOf(event.archiveActorUserId()));
        if (sourceMapper.insert(source) != 1) {
            throw new IllegalStateException("SATISFACTION_SOURCE_INSERT_CONFLICT");
        }
        return source;
    }

    private void insertFiles(SatisfactionResultVersionChangedMessage event, Long sourceId) {
        for (SatisfactionResultVersionChangedMessage.FileFact file : event.files()) {
            ProjectDeliverableSourceAttachmentDO row = new ProjectDeliverableSourceAttachmentDO();
            row.setId(IdWorker.getId()); row.setTenantId(event.tenantId());
            row.setDeliverableSourceVersionId(sourceId); row.setAttachmentSequence(file.sequence());
            row.setFileArtifactId(file.artifactId()); row.setFileVersionNo(file.versionNo());
            row.setReferenceKey(file.referenceKey()); row.setArtifactVersion(file.artifactVersion());
            row.setReferenceVersion(file.referenceVersion()); row.setAvailabilityVersion(file.availabilityVersion());
            row.setScopeVersion(file.scopeVersion()); row.setFileHash(file.sha256());
            row.setCreator(String.valueOf(event.archiveActorUserId()));
            row.setUpdater(String.valueOf(event.archiveActorUserId()));
            if (attachmentMapper.insert(row) != 1) {
                throw new IllegalStateException("SATISFACTION_SOURCE_FILE_INSERT_CONFLICT");
            }
        }
    }

    private void validate(SatisfactionResultVersionChangedMessage event) {
        if (event == null || event.tenantId() == null || event.projectId() == null
                || event.projectTaskId() == null || event.projectTaskVersion() == null
                || event.projectTaskVersion() < 0 || event.resultId() == null || event.resultVersion() == null
                || event.resultVersion() <= 0 || event.resultFactVersion() == null || event.resultFactVersion() < 0
                || !List.of("RECORDED", "INVALIDATED").contains(event.changeType())
                || event.archiveActorUserId() == null || event.archiveActorUserId() <= 0
                || ("RECORDED".equals(event.changeType()) && (event.files() == null || event.files().isEmpty()))
                || (event.files() != null && event.files().stream().anyMatch(this::invalidFile))) {
            throw new IllegalArgumentException("invalid satisfaction result event");
        }
    }

    private boolean invalidFile(SatisfactionResultVersionChangedMessage.FileFact file) {
        return file == null || file.sequence() == null || file.sequence() <= 0 || file.artifactId() == null
                || file.versionNo() == null || file.referenceKey() == null || file.referenceKey().isBlank()
                || file.scopeVersion() == null || file.sha256() == null || file.sha256().length() != 64;
    }
}
