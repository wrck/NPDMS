package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetExpectation;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceCurrentReportLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceNextReportVersionQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.Actor;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.CreateDraftCommand;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.DraftContent;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.PublishCommand;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.ReportResult;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.RevokeCommand;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportCommands.UpdateDraftCommand;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_DEPENDENCY_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_INCOMPLETE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_STATE_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;

@Service
@RequiredArgsConstructor
public class AcceptanceReportCommandService {

    static final String PUBLISH_SCOPE = "POST:/pms/acceptances/{id}/report-versions/{versionId}/actions/publish";
    static final String REVOKE_SCOPE = "POST:/pms/acceptances/{id}/actions/revoke-current-version";
    private static final String ATTACHMENT_PURPOSE = "ACCEPTANCE_REPORT_ATTACHMENT";

    private final AcceptanceActivityMapper activityMapper;
    private final AcceptanceReportVersionMapper reportMapper;
    private final AcceptanceReportAttachmentMapper attachmentMapper;
    private final FileArtifactApi fileArtifactApi;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final ProjectScopeApi projectScopeApi;

    @Transactional(rollbackFor = Exception.class)
    public ReportResult createDraft(CreateDraftCommand command, Actor actor) {
        validateActor(actor);
        AcceptanceActivityDO activity = lockActivity(command.acceptanceId(), actor, command.expectedActivityVersion());
        Integer versionNo = reportMapper.selectNextVersionNo(
                new AcceptanceNextReportVersionQuery(actor.tenantId(), activity.getId()));
        if (versionNo == null || versionNo <= 0) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        AcceptanceReportVersionDO row = new AcceptanceReportVersionDO();
        row.setId(IdWorker.getId());
        row.setAcceptanceId(activity.getId());
        row.setReportVersionNo(versionNo);
        row.setReportStatus("DRAFT");
        applyContent(row, command.content());
        row.setUploaderUserId(actor.userId());
        row.setUploadTime(LocalDateTime.now());
        row.setCreator(String.valueOf(actor.userId()));
        row.setUpdater(String.valueOf(actor.userId()));
        row.setTenantId(actor.tenantId());
        if (reportMapper.insert(row) != 1) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        return new ReportResult(activity.getId(), row.getId(), versionNo, "DRAFT", null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReportResult updateDraft(UpdateDraftCommand command, Actor actor) {
        validateActor(actor);
        lockActivity(command.acceptanceId(), actor, command.expectedActivityVersion());
        AcceptanceReportVersionDO row = lockReport(command.acceptanceId(), command.reportVersionId(), actor.tenantId());
        if (!"DRAFT".equals(row.getReportStatus())
                || !Objects.equals(row.getReportVersionNo(), command.expectedReportVersionNo())) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        applyContent(row, command.content());
        row.setUpdater(String.valueOf(actor.userId()));
        if (reportMapper.updateById(row) != 1) throw exception(ACC_REPORT_VERSION_CONFLICT);
        return new ReportResult(row.getAcceptanceId(), row.getId(), row.getReportVersionNo(), "DRAFT", null, false);
    }

    public ReportResult publish(PublishCommand command, Actor actor) {
        validateActor(actor);
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), PUBLISH_SCOPE, actor.userId(), command.idempotencyKey()),
                command.requestDigest(), ReportResult.class,
                () -> publishOnce(command, actor), result -> successFacts(result, actor));
        return requireExecution(execution);
    }

    public ReportResult revoke(RevokeCommand command, Actor actor) {
        validateActor(actor);
        var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), REVOKE_SCOPE, actor.userId(), command.idempotencyKey()),
                command.requestDigest(), ReportResult.class,
                () -> revokeOnce(command, actor), result -> successFacts(result, actor));
        return requireExecution(execution);
    }

    private ReportResult publishOnce(PublishCommand command, Actor actor) {
        AcceptanceActivityDO activity = lockActivity(command.acceptanceId(), actor, command.expectedActivityVersion());
        AcceptanceReportVersionDO current = reportMapper.selectCurrentForUpdate(
                new AcceptanceCurrentReportLockQuery(actor.tenantId(), activity.getId()));
        if (!Objects.equals(current == null ? null : current.getId(), command.expectedCurrentReportVersionId())) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        AcceptanceReportVersionDO draft = lockReport(activity.getId(), command.reportVersionId(), actor.tenantId());
        if (!"DRAFT".equals(draft.getReportStatus())
                || !Objects.equals(draft.getReportVersionNo(), command.expectedReportVersionNo())) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        requireCompleteContent(draft);
        List<FileArtifactVersionFact> files = lockAttachmentSet(draft.getId());
        if ("FINAL".equals(activity.getAcceptanceType())) requireCurrentPreliminary(activity.getProjectId(), actor.tenantId());
        LocalDateTime now = LocalDateTime.now();
        String changeType = current == null ? "EFFECTIVE" : "REPLACED";
        if (current != null) {
            current.setReportStatus("SUPERSEDED");
            current.setEffectiveTo(now);
            current.setUpdater(String.valueOf(actor.userId()));
            if (reportMapper.updateById(current) != 1) throw exception(ACC_REPORT_VERSION_CONFLICT);
            draft.setPreviousVersionId(current.getId());
        }
        draft.setReportStatus("EFFECTIVE");
        draft.setEffectiveFrom(now);
        draft.setPublisherUserId(actor.userId());
        draft.setUpdater(String.valueOf(actor.userId()));
        if (reportMapper.updateById(draft) != 1) throw exception(ACC_REPORT_VERSION_CONFLICT);
        persistAttachments(actor, draft.getId(), files);
        activity.setCurrentReportVersionId(draft.getId());
        activity.setVersion(activity.getVersion() + 1);
        activity.setUpdater(String.valueOf(actor.userId()));
        if (activityMapper.updateById(activity) != 1) throw exception(ACC_REPORT_VERSION_CONFLICT);
        return new ReportResult(activity.getId(), draft.getId(), draft.getReportVersionNo(),
                "EFFECTIVE", changeType, false);
    }

    private ReportResult revokeOnce(RevokeCommand command, Actor actor) {
        AcceptanceActivityDO activity = lockActivity(command.acceptanceId(), actor, command.expectedActivityVersion());
        AcceptanceReportVersionDO current = reportMapper.selectCurrentForUpdate(
                new AcceptanceCurrentReportLockQuery(actor.tenantId(), activity.getId()));
        if (current == null || !Objects.equals(current.getId(), command.expectedCurrentReportVersionId())
                || !Objects.equals(current.getReportVersionNo(), command.expectedCurrentReportVersionNo())) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        current.setReportStatus("REVOKED");
        current.setEffectiveTo(LocalDateTime.now());
        current.setUpdater(String.valueOf(actor.userId()));
        if (reportMapper.updateById(current) != 1) throw exception(ACC_REPORT_VERSION_CONFLICT);
        activity.setCurrentReportVersionId(null);
        activity.setVersion(activity.getVersion() + 1);
        activity.setUpdater(String.valueOf(actor.userId()));
        if (activityMapper.updateById(activity) != 1) throw exception(ACC_REPORT_VERSION_CONFLICT);
        return new ReportResult(activity.getId(), current.getId(), current.getReportVersionNo(),
                "REVOKED", "REVOKED", false);
    }

    private List<FileArtifactVersionFact> lockAttachmentSet(Long reportVersionId) {
        FileReferenceSetKey key = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION",
                String.valueOf(reportVersionId), ATTACHMENT_PURPOSE);
        try {
            List<FileReferenceSetFact> inspected = fileArtifactApi.inspectReferenceSets(
                    new FileReferenceSetCollectionQuery(List.of(key), FileActionCodes.READ));
            if (inspected.size() != 1 || inspected.getFirst().activeFacts().isEmpty()) {
                throw exception(ACC_REPORT_INCOMPLETE);
            }
            FileReferenceSetFact set = inspected.getFirst();
            List<FileReferenceSetFact> locked = fileArtifactApi.lockAndRevalidateReferenceSets(
                    new FileReferenceSetCollectionRevalidationQuery(List.of(new FileReferenceSetExpectation(
                            key, set.scopeVersion(), set.activeFacts())), FileActionCodes.READ));
            if (locked.size() != 1 || !locked.getFirst().equals(set)) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
            return set.activeFacts();
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException business) {
            throw business;
        } catch (RuntimeException unavailable) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
    }

    private void persistAttachments(Actor actor, Long reportVersionId, List<FileArtifactVersionFact> files) {
        int sequence = 1;
        for (FileArtifactVersionFact fact : files) {
            if (fact.fileFactVersion() == null || fact.sha256() == null || fact.sha256().length() != 64
                    || !"AVAILABLE".equals(fact.availabilityStatus()) || !"ACTIVE".equals(fact.referenceStatus())) {
                throw exception(ACC_REPORT_INCOMPLETE);
            }
            AcceptanceReportAttachmentDO row = new AcceptanceReportAttachmentDO();
            row.setId(IdWorker.getId());
            row.setReportVersionId(reportVersionId);
            row.setAttachmentSequence(sequence++);
            row.setFileArtifactId(fact.artifactId());
            row.setFileVersionNo(fact.versionNo());
            row.setReferenceKey(fact.referenceKey());
            row.setArtifactVersion(fact.fileFactVersion().artifactVersion());
            row.setReferenceVersion(fact.fileFactVersion().referenceVersion());
            row.setAvailabilityVersion(fact.fileFactVersion().availabilityVersion());
            row.setScopeVersion(fact.scopeVersion());
            row.setFileHash(fact.sha256());
            row.setCreator(String.valueOf(actor.userId()));
            row.setUpdater(String.valueOf(actor.userId()));
            row.setTenantId(actor.tenantId());
            if (attachmentMapper.insert(row) != 1) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
    }

    private void requireCurrentPreliminary(Long projectId, Long tenantId) {
        AcceptanceActivityDO preliminary = activityMapper.selectByIdentityForUpdate(
                new cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdentityLockQuery(
                        tenantId, projectId, "PRELIMINARY"));
        if (preliminary == null || preliminary.getCurrentReportVersionId() == null) throw exception(ACC_REPORT_INCOMPLETE);
        AcceptanceReportVersionDO report = lockReport(preliminary.getId(), preliminary.getCurrentReportVersionId(), tenantId);
        if (!"EFFECTIVE".equals(report.getReportStatus())
                || attachmentMapper.selectByReportVersion(report.getId()).isEmpty()) throw exception(ACC_REPORT_INCOMPLETE);
        requireCompleteContent(report);
    }

    private AcceptanceActivityDO lockActivity(Long acceptanceId, Actor actor, Integer expectedVersion) {
        AcceptanceActivityDO row = activityMapper.selectByIdForUpdate(
                new AcceptanceActivityIdLockQuery(actor.tenantId(), acceptanceId));
        if (row == null) throw exception(ACC_REPORT_NOT_EXISTS);
        if (!Objects.equals(row.getVersion(), expectedVersion) || !"PENDING".equals(row.getActivityStatus())) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        try {
            var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    actor.tenantId(), actor.userId(), row.getProjectId(), ProjectScopeApi.ACTION_EDIT));
            if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(row.getProjectId())) {
                throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
            }
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException business) {
            throw business;
        } catch (RuntimeException unavailable) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        return row;
    }

    private AcceptanceReportVersionDO lockReport(Long acceptanceId, Long reportVersionId, Long tenantId) {
        AcceptanceReportVersionDO row = reportMapper.selectByIdForUpdate(
                new AcceptanceReportIdLockQuery(tenantId, acceptanceId, reportVersionId));
        if (row == null) throw exception(ACC_REPORT_NOT_EXISTS);
        return row;
    }

    private void requireCompleteContent(AcceptanceReportVersionDO row) {
        if (row.getAcceptanceTime() == null || blank(row.getConclusionCode()) || blank(row.getAcceptorName())) {
            throw exception(ACC_REPORT_INCOMPLETE);
        }
    }

    private void applyContent(AcceptanceReportVersionDO row, DraftContent content) {
        if (content == null) throw exception(ACC_REPORT_STATE_INVALID);
        row.setAcceptanceTime(content.acceptanceTime());
        row.setConclusionCode(trim(content.conclusionCode()));
        row.setConclusionText(trim(content.conclusionText()));
        row.setAcceptorName(trim(content.acceptorName()));
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(ReportResult result, Actor actor) {
        AcceptanceActivityDO activity = activityMapper.selectById(result.acceptanceId());
        AcceptanceReportVersionDO report = reportMapper.selectById(result.reportVersionId());
        if (activity == null || report == null || report.getPublisherUserId() == null) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        List<FileArtifactVersionFact> files = "REVOKED".equals(result.changeType()) ? List.of()
                : attachmentMapper.selectByReportVersion(report.getId()).stream().map(this::toFact).toList();
        String eventId = UUID.randomUUID().toString();
        AcceptanceReportVersionChangedMessage message = new AcceptanceReportVersionChangedMessage(eventId,
                actor.tenantId(), result.changeType(), activity.getId(), activity.getProjectId(),
                activity.getAcceptanceType(), report.getPublisherUserId(),
                "REVOKED".equals(result.changeType()) ? null : report.getId(),
                "REVOKED".equals(result.changeType()) ? report.getId() : report.getPreviousVersionId(),
                report.getReportVersionNo(), files);
        String closureEventId = UUID.randomUUID().toString();
        return new PlatformCommandExecutionApi.SuccessFacts("ACCEPTANCE_REPORT_" + result.changeType(),
                "AcceptanceActivity", String.valueOf(activity.getId()), actor.correlationId(),
                JsonUtils.toJsonString(result), List.of(
                new PlatformCommandExecutionApi.BusinessEvent(eventId, "AcceptanceReportVersionChanged",
                        JsonUtils.toJsonString(message)),
                new PlatformCommandExecutionApi.BusinessEvent(closureEventId,
                        "ClosureGateRecheckRequested", JsonUtils.toJsonString(Map.of(
                        "eventId", closureEventId,
                        "acceptanceId", result.acceptanceId(),
                        "reportVersionId", result.reportVersionId(),
                        "changeType", result.changeType())))));
    }

    private FileArtifactVersionFact toFact(AcceptanceReportAttachmentDO row) {
        return new FileArtifactVersionFact(row.getFileArtifactId(), row.getFileVersionNo(), row.getReferenceKey(),
                "ACCEPTANCE_REPORT_ATTACHMENT", null, null, null, row.getFileHash(), "AVAILABLE", "ACTIVE",
                new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion(row.getArtifactVersion(),
                        row.getReferenceVersion(), row.getAvailabilityVersion()), row.getScopeVersion());
    }

    private ReportResult requireExecution(PlatformCommandExecutionApi.ExecutionResult<ReportResult> execution) {
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        ReportResult result = execution.response();
        return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                ? new ReportResult(result.acceptanceId(), result.reportVersionId(), result.reportVersionNo(),
                result.reportStatus(), result.changeType(), true) : result;
    }

    private void validateActor(Actor actor) {
        if (actor == null || !Objects.equals(actor.tenantId(), TenantContextHolder.getRequiredTenantId())
                || actor.userId() == null || actor.userId() <= 0 || blank(actor.correlationId())) {
            throw exception(ACC_REPORT_STATE_INVALID);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
}
