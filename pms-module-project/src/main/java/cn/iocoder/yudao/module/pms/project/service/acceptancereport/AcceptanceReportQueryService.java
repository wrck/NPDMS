package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceActivityDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportAttachmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.AcceptanceReportVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceActivityMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportAttachmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.AcceptanceReportVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.ProjectDeliverableSourceVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_DEPENDENCY_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_SCOPE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class AcceptanceReportQueryService {

    private static final String OWNER_CONTEXT = "ACC";
    private static final String OBJECT_TYPE = "ACCEPTANCE_REPORT_VERSION";
    private static final String PURPOSE_CODE = "ACCEPTANCE_REPORT_ATTACHMENT";

    private final AcceptanceActivityMapper activityMapper;
    private final AcceptanceReportVersionMapper reportMapper;
    private final AcceptanceReportAttachmentMapper attachmentMapper;
    private final ProjectScopeApi projectScopeApi;
    private final FileArtifactApi fileArtifactApi;
    private final ProjectDeliverableSourceVersionMapper sourceVersionMapper;

    public List<ActivityView> list(Long projectId, Actor actor) {
        Set<Long> projectIds = projectId == null
                ? projectScopeApi.resolveAllCurrent(new ProjectAllScopeQuery(
                actor.tenantId(), actor.userId(), ProjectScopeApi.ACTION_VIEW))
                : requireProjectScope(projectId, actor, ProjectScopeApi.ACTION_VIEW);
        if (projectIds == null || projectIds.isEmpty()) return List.of();
        return activityMapper.selectByProjectScope(new AcceptanceActivityScopeQuery(actor.tenantId(), projectIds))
                .stream().map(this::toActivityView).toList();
    }

    public ActivityView get(Long acceptanceId, Actor actor) {
        return toActivityView(requireActivity(acceptanceId, actor, ProjectScopeApi.ACTION_VIEW));
    }

    public List<ReportVersionView> listVersions(Long acceptanceId, Actor actor) {
        requireActivity(acceptanceId, actor, ProjectScopeApi.ACTION_VIEW);
        return reportMapper.selectByAcceptanceId(acceptanceId).stream()
                .map(row -> toReportView(row, attachmentMapper.selectByReportVersion(row.getId())))
                .toList();
    }

    public AttachmentView getDownloadFact(Long acceptanceId, Long reportVersionId,
                                          Integer sequence, Actor actor) {
        requireActivity(acceptanceId, actor, ProjectScopeApi.ACTION_VIEW);
        AcceptanceReportVersionDO report = reportMapper.selectById(reportVersionId);
        if (report == null || !Objects.equals(report.getAcceptanceId(), acceptanceId)) {
            throw exception(ACC_REPORT_NOT_EXISTS);
        }
        AcceptanceReportAttachmentDO attachment = attachmentMapper.selectByReportVersion(reportVersionId).stream()
                .filter(row -> Objects.equals(row.getAttachmentSequence(), sequence)).findFirst()
                .orElseThrow(() -> exception(ACC_REPORT_NOT_EXISTS));
        FileArtifactVersionFact fact;
        try {
            fact = fileArtifactApi.inspect(new FileArtifactVersionQuery(
                    attachment.getFileArtifactId(), attachment.getFileVersionNo(), OWNER_CONTEXT, OBJECT_TYPE,
                    String.valueOf(reportVersionId), PURPOSE_CODE, attachment.getReferenceKey(),
                    FileActionCodes.DOWNLOAD));
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException business) {
            throw business;
        } catch (RuntimeException unavailable) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        if (!matches(attachment, fact)) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        return toAttachmentView(attachment);
    }

    private AcceptanceActivityDO requireActivity(Long acceptanceId, Actor actor, String action) {
        AcceptanceActivityDO row = activityMapper.selectById(acceptanceId);
        if (row == null || !Objects.equals(row.getTenantId(), actor.tenantId())) {
            throw exception(ACC_REPORT_NOT_EXISTS);
        }
        requireProjectScope(row.getProjectId(), actor, action);
        return row;
    }

    private Set<Long> requireProjectScope(Long projectId, Actor actor, String action) {
        try {
            var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    actor.tenantId(), actor.userId(), projectId, action));
            if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
                throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
            }
            return scope.fullProjectIds();
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException business) {
            throw business;
        } catch (RuntimeException unavailable) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
    }

    private ActivityView toActivityView(AcceptanceActivityDO row) {
        return new ActivityView(row.getId(), row.getProjectId(), row.getProjectTaskId(), row.getExecutionContractId(),
                row.getAcceptanceType(), row.getActivityStatus(), row.getCurrentReportVersionId(), row.getVersion());
    }

    private ReportVersionView toReportView(AcceptanceReportVersionDO row,
                                           List<AcceptanceReportAttachmentDO> attachments) {
        var source = sourceVersionMapper.selectByReportVersionId(row.getId());
        return new ReportVersionView(row.getId(), row.getAcceptanceId(), row.getReportVersionNo(),
                row.getReportStatus(), row.getAcceptanceTime(), row.getConclusionCode(), row.getConclusionText(),
                row.getAcceptorName(), row.getPreviousVersionId(), row.getEffectiveFrom(), row.getEffectiveTo(),
                row.getUploaderUserId(), row.getPublisherUserId(), source == null ? null : source.getArchiveStatus(),
                source == null ? null : source.getArchiveFailureCode(),
                source == null ? null : source.getArchiveRetryCount(),
                attachments.stream().map(this::toAttachmentView).toList());
    }

    private AttachmentView toAttachmentView(AcceptanceReportAttachmentDO row) {
        return new AttachmentView(row.getAttachmentSequence(), row.getFileArtifactId(), row.getFileVersionNo(),
                row.getReferenceKey(), row.getArtifactVersion(), row.getReferenceVersion(),
                row.getAvailabilityVersion(), row.getScopeVersion(), row.getFileHash());
    }

    private boolean matches(AcceptanceReportAttachmentDO row, FileArtifactVersionFact fact) {
        return fact != null && fact.fileFactVersion() != null
                && Objects.equals(row.getFileArtifactId(), fact.artifactId())
                && Objects.equals(row.getFileVersionNo(), fact.versionNo())
                && Objects.equals(row.getReferenceKey(), fact.referenceKey())
                && Objects.equals(row.getArtifactVersion(), fact.fileFactVersion().artifactVersion())
                && Objects.equals(row.getReferenceVersion(), fact.fileFactVersion().referenceVersion())
                && Objects.equals(row.getAvailabilityVersion(), fact.fileFactVersion().availabilityVersion())
                && Objects.equals(row.getScopeVersion(), fact.scopeVersion())
                && Objects.equals(row.getFileHash(), fact.sha256())
                && "AVAILABLE".equals(fact.availabilityStatus()) && "ACTIVE".equals(fact.referenceStatus());
    }

    public record Actor(Long tenantId, Long userId) {
    }

    public record ActivityView(Long id, Long projectId, Long projectTaskId, Long executionContractId,
                               String acceptanceType, String activityStatus, Long currentReportVersionId,
                               Integer version) {
    }

    public record ReportVersionView(Long id, Long acceptanceId, Integer reportVersionNo, String reportStatus,
                                    LocalDateTime acceptanceTime, String conclusionCode, String conclusionText,
                                    String acceptorName, Long previousVersionId, LocalDateTime effectiveFrom,
                                    LocalDateTime effectiveTo, Long uploaderUserId, Long publisherUserId,
                                    String archiveStatus, String archiveFailureCode, Integer archiveRetryCount,
                                    List<AttachmentView> attachments) {
    }

    public record AttachmentView(Integer sequence, Long artifactId, Integer versionNo, String referenceKey,
                                 Integer artifactVersion, Integer referenceVersion, Integer availabilityVersion,
                                 Long scopeVersion, String fileHash) {
    }
}
