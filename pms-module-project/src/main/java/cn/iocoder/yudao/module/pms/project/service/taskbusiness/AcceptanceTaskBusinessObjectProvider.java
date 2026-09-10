package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AccProjectDeliverableDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AccProjectDeliverableMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceActivityIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.AcceptanceReportIdLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query.DeliverableSourceIdLockQuery;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.AcceptanceReportQueryService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** ACC-03/04, PM-03/11: references existing activities only; Q-TPLACC-001 still blocks independent creation. */
@Service
@RequiredArgsConstructor
public class AcceptanceTaskBusinessObjectProvider implements TaskBusinessObjectProvider {

    private final AcceptanceReportQueryService queryService;
    private final AcceptanceActivityMapper activityMapper;
    private final AcceptanceReportVersionMapper reportMapper;
    private final AcceptanceReportAttachmentMapper attachmentMapper;
    private final AccProjectDeliverableMapper deliverableMapper;
    private final ProjectDeliverableSourceVersionMapper sourceMapper;
    private final ProjectDeliverableSourceAttachmentMapper sourceAttachmentMapper;
    private final FileArtifactApi fileArtifactApi;
    private final ProjectScopeApi projectScopeApi;
    private final PermissionApi permissionApi;

    @Override public String ownerContext() { return "ACC"; }
    @Override public String objectType() { return "ACCEPTANCE"; }
    @Override public Set<String> completionFactCodes() { return Set.of("REPORT_EFFECTIVE"); }

    @Override
    public Set<String> inspectContext(Context context) {
        requireQuery(context, false);
        return canManage(context) ? Set.of("QUERY", "MANAGE") : Set.of("QUERY");
    }

    @Override
    public List<BusinessObjectFact> candidates(Context context) {
        requireQuery(context, false);
        // Existing identity is unique by project + PRELIMINARY/FINAL; no new independent activities.
        return queryService.list(context.projectId(), actor(context)).stream()
                .map(activity -> toFact(context, requireActivity(context, activity, activity.id()), false)).toList();
    }

    @Override
    public BusinessObjectFact inspect(Context context, String objectId) {
        requireQuery(context, false);
        Long id = objectId(objectId);
        return toFact(context, requireActivity(context, queryService.get(id, actor(context)), id), false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public BusinessObjectFact lockAndRevalidate(Context context, String objectId, String expectedVersion) {
        requireQuery(context, true);
        Long id = objectId(objectId);
        // Reuse the query service's current scope check before locking the actual Owner row.
        requireActivity(context, queryService.get(id, actor(context)), id);
        AcceptanceActivityDO row = activityMapper.selectByIdForUpdate(
                new AcceptanceActivityIdLockQuery(context.tenantId(), id));
        if (row == null || Boolean.TRUE.equals(row.getDeleted())
                || !Objects.equals(row.getTenantId(), context.tenantId())) throw exception(ACC_REPORT_NOT_EXISTS);
        var activity = requireActivity(context, new AcceptanceReportQueryService.ActivityView(row.getId(),
                row.getProjectId(), row.getProjectTaskId(), row.getExecutionContractId(), row.getAcceptanceType(),
                row.getActivityStatus(), row.getCurrentReportVersionId(), row.getVersion()), id);
        BusinessObjectFact fact = toFact(context, activity, true);
        if (expectedVersion == null || !expectedVersion.equals(fact.factVersion())) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        return fact;
    }

    private BusinessObjectFact toFact(Context context, AcceptanceReportQueryService.ActivityView activity, boolean lock) {
        AcceptanceReportVersionDO report = currentReport(context, activity, lock);
        List<FileArtifactVersionFact> files = List.of();
        List<BusinessArtifact> artifacts = List.of();
        boolean effective = false;
        if (report != null && "EFFECTIVE".equals(report.getReportStatus()) && report.getEffectiveTo() == null) {
            List<AcceptanceReportAttachmentDO> attachments = attachmentMapper.selectByReportVersion(report.getId());
            for (var attachment : attachments) {
                if (Boolean.TRUE.equals(attachment.getDeleted())
                        || !Objects.equals(attachment.getTenantId(), context.tenantId())
                        || !Objects.equals(attachment.getReportVersionId(), report.getId())
                        || attachment.getAttachmentSequence() == null || attachment.getAttachmentSequence() <= 0
                        || attachment.getFileArtifactId() == null || attachment.getFileArtifactId() <= 0
                        || attachment.getFileVersionNo() == null || attachment.getFileVersionNo() <= 0
                        || !uuid(attachment.getReferenceKey()) || attachment.getArtifactVersion() == null
                        || attachment.getArtifactVersion() < 0 || attachment.getReferenceVersion() == null
                        || attachment.getReferenceVersion() < 0 || attachment.getAvailabilityVersion() == null
                        || attachment.getAvailabilityVersion() < 0 || attachment.getScopeVersion() == null
                        || attachment.getScopeVersion() < 0 || attachment.getFileHash() == null
                        || !attachment.getFileHash().matches("[0-9a-fA-F]{64}")) {
                    throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
                }
            }
            // Preserve Owner lock order: activity -> report -> deliverable -> source -> PLT files.
            var source = archivedSource(context, activity, report, lock);
            files = inspectFiles(report, attachments, lock);
            effective = report.getAcceptanceTime() != null && notBlank(report.getConclusionCode())
                    && notBlank(report.getAcceptorName()) && !attachments.isEmpty()
                    && matchesAttachments(attachments, files);
            if (effective && source != null) artifacts = artifacts(context, report, source, attachments, files);
        }
        Set<String> actions = new LinkedHashSet<>(Set.of("QUERY"));
        if (canManage(context)) {
            actions.add("MANAGE");
            var relationScope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(),
                    context.projectId(), ProjectScopeApi.ACTION_MANAGE));
            if (relationScope != null && relationScope.fullProjectIds() != null
                    && relationScope.fullProjectIds().contains(context.projectId())) actions.addAll(Set.of("LINK", "UNLINK"));
            if ("PENDING".equals(activity.activityStatus()) && activity.projectTaskId() != null
                    && activity.projectTaskId() > 0 && activity.executionContractId() != null && activity.executionContractId() > 0) {
                // Existing task-bound draft commands only. MANAGE is not itself a report write action.
                actions.add("UPDATE");
                if (report != null && "EFFECTIVE".equals(report.getReportStatus()) && report.getEffectiveTo() == null) {
                    actions.add("REVOKE");
                }
                // PUBLISH is deliberately absent: the current command hard-codes preliminary acceptance,
                // and no frozen scope/prerequisite query contract can prove the new configured admission.
                // FILE_WRITE is not an existing ACC action; PLT retains its separate upload/reference policy.
            }
        }
        // No qualification API currently proves conclusion + frozen prerequisites + applicable scope coverage.
        // Neither COMPLETED nor a non-empty (even PASS) conclusion supplies ACCEPTANCE_PASSED.
        return new BusinessObjectFact(activity.id().toString(),
                ("PRELIMINARY".equals(activity.acceptanceType()) ? "初验" : "终验") + " #" + activity.id(),
                "ACC_ACCEPTANCE:v1:" + digest(Arrays.asList(activity, report, files, effective, artifacts)),
                actions, Map.of("REPORT_EFFECTIVE", effective), artifacts);
    }

    private AcceptanceReportVersionDO currentReport(Context context, AcceptanceReportQueryService.ActivityView activity,
                                                     boolean lock) {
        if (activity.currentReportVersionId() == null) return null;
        var report = lock ? reportMapper.selectByIdForUpdate(new AcceptanceReportIdLockQuery(
                context.tenantId(), activity.id(), activity.currentReportVersionId()))
                : reportMapper.selectById(activity.currentReportVersionId());
        if (report == null || Boolean.TRUE.equals(report.getDeleted())
                || !Objects.equals(report.getTenantId(), context.tenantId())
                || !Objects.equals(report.getId(), activity.currentReportVersionId())
                || !Objects.equals(report.getAcceptanceId(), activity.id())) throw exception(ACC_REPORT_NOT_EXISTS);
        if (report.getReportVersionNo() == null || report.getReportVersionNo() <= 0) {
            throw exception(ACC_REPORT_VERSION_CONFLICT);
        }
        return report;
    }

    private List<FileArtifactVersionFact> inspectFiles(AcceptanceReportVersionDO report,
                                                      List<AcceptanceReportAttachmentDO> attachments, boolean lock) {
        if (attachments.isEmpty()) return List.of();
        var key = new FileReferenceSetKey("ACC", "ACCEPTANCE_REPORT_VERSION", report.getId().toString(),
                "ACCEPTANCE_REPORT_ATTACHMENT");
        List<FileReferenceSetFact> sets;
        if (lock) {
            var expected = attachments.stream().map(a -> new FileArtifactVersionFact(a.getFileArtifactId(),
                    a.getFileVersionNo(), a.getReferenceKey(), null, null, null, null, a.getFileHash(),
                    "AVAILABLE", "ACTIVE", new FileFactVersion(a.getArtifactVersion(), a.getReferenceVersion(),
                    a.getAvailabilityVersion()), a.getScopeVersion()))
                    .sorted(Comparator.comparing(FileArtifactVersionFact::referenceKey)).toList();
            sets = fileArtifactApi.lockAndRevalidateReferenceSets(new FileReferenceSetCollectionRevalidationQuery(
                    List.of(new FileReferenceSetExpectation(key, expected.getFirst().scopeVersion(), expected)),
                    FileActionCodes.READ));
        } else {
            sets = fileArtifactApi.inspectReferenceSets(new FileReferenceSetCollectionQuery(List.of(key), FileActionCodes.READ));
        }
        if (sets == null || sets.size() != 1 || !key.equals(sets.getFirst().key())) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        return sets.getFirst().activeFacts();
    }

    private boolean matchesAttachments(List<AcceptanceReportAttachmentDO> attachments, List<FileArtifactVersionFact> files) {
        if (attachments.size() != files.size()
                || attachments.stream().map(AcceptanceReportAttachmentDO::getAttachmentSequence).distinct().count() != attachments.size()
                || attachments.stream().map(AcceptanceReportAttachmentDO::getReferenceKey).distinct().count() != attachments.size()) {
            return false;
        }
        return attachments.stream().allMatch(a -> files.stream().anyMatch(f ->
                Objects.equals(a.getFileArtifactId(), f.artifactId()) && Objects.equals(a.getFileVersionNo(), f.versionNo())
                && Objects.equals(a.getReferenceKey(), f.referenceKey()) && f.fileFactVersion() != null
                && Objects.equals(a.getArtifactVersion(), f.fileFactVersion().artifactVersion())
                && Objects.equals(a.getReferenceVersion(), f.fileFactVersion().referenceVersion())
                && Objects.equals(a.getAvailabilityVersion(), f.fileFactVersion().availabilityVersion())
                && Objects.equals(a.getScopeVersion(), f.scopeVersion()) && Objects.equals(a.getFileHash(), f.sha256())
                && "AVAILABLE".equals(f.availabilityStatus()) && "ACTIVE".equals(f.referenceStatus())));
    }

    private ProjectDeliverableSourceVersionDO archivedSource(Context context,
            AcceptanceReportQueryService.ActivityView activity, AcceptanceReportVersionDO report, boolean lock) {
        var snapshot = sourceMapper.selectByReportVersionId(report.getId());
        if (snapshot == null) return null; // Async projection not formed: never pretend it is archived.
        if (!Objects.equals(snapshot.getTenantId(), context.tenantId()) || Boolean.TRUE.equals(snapshot.getDeleted())) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        AccProjectDeliverableDO deliverable = lock
                ? deliverableMapper.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(context.tenantId(), snapshot.getDeliverableId()))
                : deliverableMapper.selectById(snapshot.getDeliverableId());
        var source = lock ? sourceMapper.selectByIdForUpdate(new DeliverableSourceIdLockQuery(
                context.tenantId(), snapshot.getId())) : snapshot;
        String code = "PRELIMINARY".equals(activity.acceptanceType()) ? "D-INITIAL-REPORT" : "D-FINAL-REPORT";
        if (deliverable == null || source == null || Boolean.TRUE.equals(deliverable.getDeleted())
                || Boolean.TRUE.equals(source.getDeleted()) || !Objects.equals(deliverable.getTenantId(), context.tenantId())
                || !Objects.equals(source.getTenantId(), context.tenantId())
                || !Objects.equals(deliverable.getProjectId(), context.projectId())
                || !Objects.equals(deliverable.getId(), source.getDeliverableId())
                || !Objects.equals(source.getId(), snapshot.getId())
                || !code.equals(deliverable.getDeliverableCode()) || !"AcceptanceReportVersion".equals(source.getSourceObjectType())
                || !"ACC-03@V1".equals(source.getSourceRequirementId())
                || !Objects.equals(source.getSourceObjectId(), report.getId())
                || !Objects.equals(source.getSourceVersion(), report.getReportVersionNo())) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        return Objects.equals(deliverable.getCurrentSourceVersionId(), source.getId())
                && "CURRENT".equals(source.getRelationStatus()) && "ARCHIVED".equals(source.getArchiveStatus())
                && "ARCHIVED".equals(deliverable.getArchiveStatus()) && source.getArchiveTime() != null ? source : null;
    }

    private List<BusinessArtifact> artifacts(Context context, AcceptanceReportVersionDO report,
            ProjectDeliverableSourceVersionDO source, List<AcceptanceReportAttachmentDO> attachments,
            List<FileArtifactVersionFact> files) {
        var rows = sourceAttachmentMapper.selectBySourceVersion(source.getId());
        if (rows.size() != attachments.size()) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        List<BusinessArtifact> result = new ArrayList<>();
        Set<Integer> sequences = new HashSet<>();
        for (var row : rows) {
            if (Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(), context.tenantId())
                    || !Objects.equals(row.getDeliverableSourceVersionId(), source.getId())
                    || !sequences.add(row.getAttachmentSequence())) throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
            var attachment = attachments.stream().filter(a -> Objects.equals(a.getAttachmentSequence(), row.getAttachmentSequence()))
                    .findFirst().orElseThrow(() -> exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE));
            if (!Objects.equals(row.getFileArtifactId(), attachment.getFileArtifactId())
                    || !Objects.equals(row.getFileVersionNo(), attachment.getFileVersionNo())
                    || !Objects.equals(row.getReferenceKey(), attachment.getReferenceKey())
                    || !Objects.equals(row.getArtifactVersion(), attachment.getArtifactVersion())
                    || !Objects.equals(row.getReferenceVersion(), attachment.getReferenceVersion())
                    || !Objects.equals(row.getAvailabilityVersion(), attachment.getAvailabilityVersion())
                    || !Objects.equals(row.getScopeVersion(), attachment.getScopeVersion())
                    || !Objects.equals(row.getFileHash(), attachment.getFileHash()) || !uuid(row.getReferenceKey())) {
                throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
            }
            var file = files.stream().filter(f -> row.getReferenceKey().equals(f.referenceKey())).findFirst().orElseThrow();
            // The actual archive operation preserves this exact ACTIVE attachment tuple and UUID in its
            // separate ARCHIVED set. Do not invoke archiveReferenceSets (a write) during task inspection.
            result.add(new BusinessArtifact(row.getFileArtifactId().toString(), row.getFileVersionNo(), row.getReferenceKey(),
                    file.name(), "ACC_REPORT:" + report.getId() + ":" + source.getSourceVersion() + ":SOURCE:" + source.getId()));
        }
        return List.copyOf(result);
    }

    private void requireQuery(Context context, boolean lock) {
        if (context == null || context.tenantId() == null || context.tenantId() < 0
                || context.actorId() == null || context.actorId() <= 0 || context.projectId() == null || context.projectId() <= 0
                || context.taskId() == null || context.taskId() <= 0
                || !Objects.equals(context.tenantId(), TenantContextHolder.getTenantId())
                || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())) throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
        if (!permissionApi.hasAnyPermissions(context.actorId(), "pms:acceptance:report:query")) throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(),
                context.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(context.projectId())) throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
        if (lock) {
            var locked = projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(context.tenantId(), context.actorId(),
                    context.projectId(), ProjectScopeApi.ACTION_VIEW, scope.treeVersion()));
            if (locked == null || !Objects.equals(scope.treeVersion(), locked.treeVersion())
                    || locked.fullProjectIds() == null || !locked.fullProjectIds().contains(context.projectId())) {
                throw exception(ACC_REPORT_SCOPE_FORBIDDEN);
            }
        }
    }

    private boolean canManage(Context context) {
        // Existing report commands use PROJECT_EDIT, not a guessed role or template configuration permission.
        if (!permissionApi.hasAnyPermissions(context.actorId(), "pms:acceptance:report:write")) return false;
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(),
                context.projectId(), ProjectScopeApi.ACTION_EDIT));
        return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(context.projectId());
    }

    private AcceptanceReportQueryService.ActivityView requireActivity(Context context,
            AcceptanceReportQueryService.ActivityView activity, Long expectedId) {
        if (activity == null || !Objects.equals(activity.id(), expectedId) || expectedId == null || expectedId <= 0
                || !Objects.equals(activity.projectId(), context.projectId())) throw exception(ACC_REPORT_NOT_EXISTS);
        if (activity.version() == null || activity.version() < 0) throw exception(ACC_REPORT_VERSION_CONFLICT);
        if (!("PRELIMINARY".equals(activity.acceptanceType()) || "FINAL".equals(activity.acceptanceType()))
                || !("PENDING".equals(activity.activityStatus()) || "COMPLETED".equals(activity.activityStatus()))) {
            throw exception(ACC_REPORT_STATE_INVALID);
        }
        return activity;
    }

    private AcceptanceReportQueryService.Actor actor(Context context) {
        return new AcceptanceReportQueryService.Actor(context.tenantId(), context.actorId());
    }
    private Long objectId(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]*")) throw exception(ACC_REPORT_NOT_EXISTS);
            return Long.valueOf(value);
        } catch (NumberFormatException invalid) { throw exception(ACC_REPORT_NOT_EXISTS); }
    }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static boolean uuid(String value) {
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (RuntimeException invalid) { return false; }
    }
    private static String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}
