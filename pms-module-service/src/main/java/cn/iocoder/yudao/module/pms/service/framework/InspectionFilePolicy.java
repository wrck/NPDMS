package cn.iocoder.yudao.module.pms.service.framework;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection.InspectionTaskDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.*;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.query.InspectionTaskRows;
import cn.iocoder.yudao.module.pms.service.service.inspection.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@RequiredArgsConstructor
public class InspectionFilePolicy implements FileBusinessObjectPolicyProvider, FileDocumentSourceProvider {
    private static final Set<String> READ = Set.of(FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);
    private static final Set<String> WRITE = Set.of(FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.REPLACE, FileActionCodes.DETACH);
    private static final Set<String> REPORT_MEDIA = Set.of("application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/xml", "text/xml");
    private static final Set<String> EVIDENCE_MEDIA = Set.of("application/pdf", "image/png", "image/jpeg", "text/plain",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final InspectionTaskMapper tasks;
    private final InspectionReportFileMapper reportFiles;
    private final InspectionExecutionMapper executions;
    private final InspectionEvidenceMapper evidence;
    private final ServiceIssueMapper issues;
    private final InspectionAccessService access;
    private final PermissionApi permissions;

    @Override public String ownerContext() { return "SRV"; }
    @Override public String objectType() { return "INSPECTION_TASK"; }
    @Override public List<Descriptor> descriptors() {
        return List.of(new Descriptor("INS.REPORT", "巡检报告"), new Descriptor("INS.LOG", "巡检日志"),
                new Descriptor("INS.EVIDENCE", "巡检处理证据"), new Descriptor("INS.ATTACHMENT", "巡检附件"));
    }

    @Override public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) {
        return resolve(new Resolution(query, false, null, null, false, null, false));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return resolve(new Resolution(query.toInspectionQuery(), true, query.expectedScopeVersion(), null, false, null, false));
    }
    @Override public FileBusinessObjectPolicyFact inspectFrozenFileVersion(FrozenFileVersionAccessQuery query) {
        return resolve(new Resolution(query.scope(), false, query.expectedScopeVersion(), query, false, null, false));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateFrozenFileVersion(FrozenFileVersionAccessQuery query) {
        return resolve(new Resolution(query.scope(), true, query.expectedScopeVersion(), query, false, null, false));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateNativeGeneratedFile(NativeGeneratedFilePolicyQuery query) {
        return resolve(new Resolution(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(), query.ownerContext(),
                query.objectType(), String.valueOf(query.objectId()), query.purposeCode(), "generated", FileActionCodes.UPLOAD),
                true, query.expectedScopeVersion(), null, true, query.expectedOwnerVersion(), false));
    }
    @Override public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        var key = query.key();
        return resolve(new Resolution(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(), key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode(), "reference-set", query.requiredAction()), false, null, null, false, null, true));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query) {
        var key = query.key();
        return resolve(new Resolution(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(), key.ownerContext(),
                key.objectType(), key.objectId(), key.purposeCode(), "reference-set", query.requiredAction()),
                true, query.expectedScopeVersion(), null, false, null, true));
    }

    private record Resolution(FileBusinessObjectPolicyQuery query, boolean lock, Long expectedScopeVersion,
                              FrozenFileVersionAccessQuery frozen, boolean nativeFile, Long expectedOwnerVersion,
                              boolean referenceSet) { }
    private FileBusinessObjectPolicyFact resolve(Resolution request) {
        try {
            return resolvePolicy(request);
        } catch (InspectionException failure) {
            if (failure.getReason() == InspectionException.Reason.FORBIDDEN) return denied();
            throw failure;
        }
    }

    private FileBusinessObjectPolicyFact resolvePolicy(Resolution request) {
        var query = request.query();
        if (!Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId()) || !ownerContext().equals(query.ownerContext())
                || !objectType().equals(query.objectType()) || !Set.of("LOG", "REPORT", "EVIDENCE", "ATTACHMENT").contains(query.purposeCode())) return denied();
        Long id;
        try { id = Long.valueOf(query.objectId()); } catch (NumberFormatException invalid) { return denied(); }
        var task = tasks.selectById(id);
        if (task == null || !Objects.equals(task.getTenantId(), query.tenantId())) return denied();
        var context = InspectionRequestContext.builder().tenantId(query.tenantId()).actorId(query.actorUserId()).build();
        var project = access.project(new InspectionAccessService.ProjectAccess(context, task.getProjectId(), request.lock()));
        if (request.expectedScopeVersion() != null && !Objects.equals(project.scopeVersion(), request.expectedScopeVersion())) return denied();
        boolean manager = access.manager(new InspectionAccessService.ProjectAccess(context, task.getProjectId(), request.lock()));
        if (request.lock()) task = tasks.selectForUpdate(new InspectionTaskRows(query.tenantId(), id));
        if (task == null || request.expectedOwnerVersion() != null && !Objects.equals(task.getVersion(), request.expectedOwnerVersion())) return denied();
        boolean owner = Objects.equals(query.actorUserId(), task.getOwnerUserId());
        boolean reading = READ.contains(query.requiredAction()); boolean writing = WRITE.contains(query.requiredAction());
        boolean terminal = Set.of("ARCHIVED", "CANCELLED").contains(task.getStatusCode());
        boolean allowed = false;
        var rows = new InspectionTaskRows(query.tenantId(), id);
        if (reading) {
            allowed = (owner || manager) && permissions.hasAnyPermissions(query.actorUserId(), "pms:inspection-task:query");
            if (!allowed && !request.referenceSet() && "EVIDENCE".equals(query.purposeCode()))
                allowed = issueAccess(new IssueAccess(query, task, false, request.lock()));
        } else if (writing && (!terminal || "CANCELLED".equals(task.getStatusCode()) && "EVIDENCE".equals(query.purposeCode()))) {
            allowed = switch (query.purposeCode()) {
                case "LOG" -> request.nativeFile() && (owner || manager) && "EXECUTING".equals(task.getStatusCode())
                        && permissions.hasAnyPermissions(query.actorUserId(), "pms:inspection-task:execute");
                case "REPORT" -> (owner || manager) && Set.of("REPORT_PENDING", "ANNOTATION_PENDING").contains(task.getStatusCode())
                        && permissions.hasAnyPermissions(query.actorUserId(), "pms:inspection-report:generate");
                case "EVIDENCE" -> request.referenceSet() ? issueSetAccess(request, task)
                        : issueAccess(new IssueAccess(query, task, true, request.lock()));
                case "ATTACHMENT" -> (owner || manager) && permissions.hasAnyPermissions(query.actorUserId(), "pms:inspection-task:prepare");
                default -> false;
            };
            if (allowed && !request.referenceSet()
                    && frozenReference(new FrozenReference(rows, query.purposeCode(), query.referenceKey(), null, null))) allowed = false;
        }
        if (allowed && request.frozen() != null) allowed = frozenReference(new FrozenReference(rows, query.purposeCode(),
                query.referenceKey(), request.frozen().artifactId(), request.frozen().versionNo()));
        Set<String> media = "LOG".equals(query.purposeCode()) ? Set.of("text/plain", "application/zip", "application/json")
                : "REPORT".equals(query.purposeCode()) ? REPORT_MEDIA : EVIDENCE_MEDIA;
        return new FileBusinessObjectPolicyFact(allowed, project.scopeVersion(), writing && allowed ? "MUTABLE" : "IMMUTABLE",
                "MULTIPLE", Set.of("INSPECTION_" + query.purposeCode()), media, 52_428_800L, "INTERNAL");
    }

    private record IssueAccess(FileBusinessObjectPolicyQuery query, InspectionTaskDO task, boolean write, boolean lock) { }

    /** Grants the write namespace only; PLT still authorizes the exact issue reference afterwards. */
    private boolean issueSetAccess(Resolution request, InspectionTaskDO task) {
        var query = request.query();
        if (!permissions.hasAnyPermissions(query.actorUserId(), "pms:service-issue:remediate")) return false;
        var rows = new InspectionTaskRows(query.tenantId(), task.getId());
        var current = request.lock() ? issues.selectForTaskForUpdate(rows) : issues.selectForTask(rows);
        return current.stream().anyMatch(issue -> Objects.equals(issue.getTenantId(), query.tenantId())
                && Objects.equals(issue.getTaskId(), task.getId())
                && Objects.equals(issue.getResponsibleUserId(), query.actorUserId())
                && !"CLOSED".equals(issue.getStatusCode()));
    }

    private boolean issueAccess(IssueAccess request) {
        var query = request.query();
        String[] parts = query.referenceKey() == null ? new String[0] : query.referenceKey().split(":", 3);
        if (parts.length != 3 || !"issue".equals(parts[0])) return false;
        Long issueId;
        try { issueId = Long.valueOf(parts[1]); } catch (NumberFormatException invalid) { return false; }
        var issue = request.lock() ? issues.selectForTaskForUpdate(new InspectionTaskRows(query.tenantId(), request.task().getId()))
                .stream().filter(row -> Objects.equals(row.getId(), issueId)).findFirst().orElse(null) : issues.selectById(issueId);
        return issue != null && Objects.equals(issue.getTenantId(), query.tenantId()) && Objects.equals(issue.getTaskId(), request.task().getId())
                && Objects.equals(issue.getResponsibleUserId(), query.actorUserId()) && (!request.write() || !"CLOSED".equals(issue.getStatusCode()))
                && permissions.hasAnyPermissions(query.actorUserId(), request.write() ? "pms:service-issue:remediate" : "pms:service-issue:query");
    }

    private record FrozenReference(InspectionTaskRows rows, String purpose, String referenceKey, Long artifactId, Integer versionNo) {
        boolean matches(String key, Long artifact, Integer version) {
            return Objects.equals(referenceKey, key) && (artifactId == null || Objects.equals(artifactId, artifact) && Objects.equals(versionNo, version));
        }
    }
    private boolean frozenReference(FrozenReference reference) {
        return switch (reference.purpose()) {
            case "REPORT" -> reportFiles.selectForTask(reference.rows()).stream().anyMatch(f -> reference.matches(f.getReferenceKey(), f.getArtifactId(), f.getFileVersionNo()));
            case "LOG" -> executions.selectForTask(reference.rows()).stream().anyMatch(f -> reference.matches(f.getFileReferenceKey(), f.getArtifactId(), f.getFileVersionNo()));
            case "EVIDENCE" -> evidence.selectForTask(reference.rows()).stream().anyMatch(f -> reference.matches(f.getReferenceKey(), f.getArtifactId(), f.getFileVersionNo()));
            default -> false;
        };
    }

    @Override public Scope resolve(Long tenantId, FileEvidenceApi.Document document) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()) || !ownerContext().equals(document.ownerContext())
                || !objectType().equals(document.objectType()) || !Set.of("LOG", "REPORT", "EVIDENCE", "ATTACHMENT").contains(document.purposeCode())) return null;
        Long taskId;
        try { taskId = Long.valueOf(document.objectId()); } catch (NumberFormatException invalid) { return null; }
        var task = tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenantId)) return null;
        Long revisionId = null;
        if ("REPORT".equals(document.purposeCode())) revisionId = reportFiles.selectForTask(new InspectionTaskRows(tenantId, taskId)).stream()
                .filter(f -> Objects.equals(f.getArtifactId(), document.artifactId()) && Objects.equals(f.getFileVersionNo(), document.versionNo()))
                .map(f -> f.getReportId()).findFirst().orElse(null);
        return new Scope(task.getProjectId(), "INS." + document.purposeCode(), "SRV", "inspectionTask", taskId, revisionId);
    }
    private static FileBusinessObjectPolicyFact denied() { return new FileBusinessObjectPolicyFact(false, null, null, null, null, null, null, null); }
}
