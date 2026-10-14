package cn.iocoder.yudao.module.pms.cutover.framework.runtime;

import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.query.CutoverChecklistRowQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.query.CutoverClosureRowQuery;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRuntimeRoleApprovalAudience;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 平台上传/预览/下载仍由PLT办理，CUT仅提供自身对象的范围与可变性。 */
public class CutoverFilePolicy implements FileBusinessObjectPolicyProvider, FileDocumentSourceProvider {
    private static final Set<String> READ = Set.of(FileActionCodes.READ, FileActionCodes.DOWNLOAD, FileActionCodes.PREVIEW);
    private static final Set<String> WRITE = Set.of(FileActionCodes.UPLOAD, FileActionCodes.REFERENCE, FileActionCodes.REPLACE, FileActionCodes.DETACH);
    private static final Set<String> MEDIA = Set.of("application/pdf", "image/jpeg", "image/png", "text/plain",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final String type;
    private final CutoverTaskMapper tasks;
    private final CutoverChecklistMapper checklists;
    private final CutoverChecklistItemMapper items;
    private final CutoverClosureMapper closures;
    private final CutoverProjectScopePort scopes;
    private final PermissionApi permissions;
    private final CutoverRuntimeRoleApprovalAudience approvalAudience;

    public CutoverFilePolicy(String type, CutoverTaskMapper tasks, CutoverChecklistMapper checklists,
            CutoverChecklistItemMapper items, CutoverClosureMapper closures, CutoverProjectScopePort scopes, PermissionApi permissions,
            CutoverRuntimeRoleApprovalAudience approvalAudience) {
        this.type = type; this.tasks = tasks; this.checklists = checklists; this.items = items;
        this.closures = closures; this.scopes = scopes; this.permissions = permissions;
        this.approvalAudience = approvalAudience;
    }
    public String ownerContext() { return "CUT"; }
    public String objectType() { return type; }
    @Override
    public List<Descriptor> descriptors() {
        return switch (type) {
            case "CUTOVER_PLAN" -> List.of(new Descriptor("CUT.PLAN", "割接方案"));
            case "CUTOVER_CHECKLIST_ITEM" -> List.of(new Descriptor("CUT.CHECKLIST_EVIDENCE", "割接清单证据"));
            case "CUTOVER_CLOSURE" -> List.of(
                    new Descriptor("CUT.POST_COLLECTION_CHECKLIST", "割接后检查清单"),
                    new Descriptor("CUT.IMPLEMENTATION_COMMITMENT", "割接实施承诺"),
                    new Descriptor("CUT.CLOSURE_EVIDENCE", "割接闭环其他证据"),
                    new Descriptor("CUT.MANUAL_COLLECTION_RESULT", "割接闭环人工采集结果"));
            default -> throw new IllegalArgumentException("unknown CUT file type");
        };
    }
    @Override
    public Scope resolve(Long tenantId, FileEvidenceApi.Document document) {
        if (!Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId())
                || !"CUT".equals(document.ownerContext()) || !type.equals(document.objectType())
                || !purposeAllowed(document.purposeCode())) return null;
        Long id;
        try { id = Long.valueOf(document.objectId()); } catch (NumberFormatException invalid) { return null; }
        var context = locate(tenantId, id, false);
        if (context == null) return null;
        // Plan files are bound to their task and retain their exact file version.
        // A historical file must not acquire the current plan revision as its source.
        return new Scope(context.task().getProjectId(), documentSource(document.purposeCode()),
                "CUT", "cutoverTask", context.task().getId(), context.documentRevisionId());
    }
    public FileBusinessObjectPolicyFact inspect(FileBusinessObjectPolicyQuery query) { return resolve(query, null, false); }
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidate(FileBusinessObjectPolicyRevalidationQuery query) {
        return resolve(query.toInspectionQuery(), query.expectedScopeVersion(), true);
    }
    public FileBusinessObjectPolicyFact inspectReferenceSet(FileBusinessObjectReferenceSetQuery query) {
        var key = query.key();
        return inspect(new FileBusinessObjectPolicyQuery(query.tenantId(), query.actorUserId(), key.ownerContext(), key.objectType(),
                key.objectId(), key.purposeCode(), "reference-set", query.requiredAction()));
    }
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateReferenceSet(FileBusinessObjectReferenceSetRevalidationQuery query) {
        var key = query.key();
        return lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(query.tenantId(), query.actorUserId(), key.ownerContext(),
                key.objectType(), key.objectId(), key.purposeCode(), "reference-set", query.requiredAction(), query.expectedScopeVersion()));
    }
    @Transactional(rollbackFor = Exception.class)
    public FileBusinessObjectPolicyFact lockAndRevalidateNativeGeneratedFile(NativeGeneratedFilePolicyQuery query) {
        if (!"CUTOVER_PLAN".equals(type)) return denied();
        var task = tasks.selectById(query.objectId());
        if (task == null || !Objects.equals(task.getTenantId(), query.tenantId())
                || !Objects.equals(task.getVersion(), query.expectedOwnerVersion())) return denied();
        long scope = query.expectedScopeVersion() == null
                ? scopes.inspect(query.actorUserId(), task.getProjectId(), "ACTION_VIEW").projectScopeVersion() : query.expectedScopeVersion();
        return lockAndRevalidate(new FileBusinessObjectPolicyRevalidationQuery(query.tenantId(), query.actorUserId(), "CUT", type,
                query.objectId().toString(), query.purposeCode(), "generated-plan", FileActionCodes.UPLOAD, scope));
    }
    private FileBusinessObjectPolicyFact resolve(FileBusinessObjectPolicyQuery query, Long expected, boolean lock) {
        CutoverOwnerAdapters.requireTenant(query.tenantId());
        if (!"CUT".equals(query.ownerContext()) || !type.equals(query.objectType()) || !purposeAllowed(query.purposeCode())) return denied();
        Long id;
        try { id = Long.valueOf(query.objectId()); } catch (NumberFormatException invalid) { return denied(); }
        var context = locate(query.tenantId(), id, false);
        if (context == null) return denied();
        boolean writing = WRITE.contains(query.requiredAction());
        String action = writing ? "ACTION_EDIT" : "ACTION_VIEW";
        if (READ.contains(query.requiredAction()) && !"CUTOVER_CLOSURE".equals(type)) {
            var audienceQuery = new CutoverRuntimeRoleApprovalAudience.FileQuery(
                    new CutoverRuntimeRoleApprovalAudience.Query(query.tenantId(), query.actorUserId(), context.task().getId(), lock ? expected : null),
                    new CutoverRuntimeRoleApprovalAudience.FileReference(type, query.objectId(), query.referenceKey()));
            var grant = lock ? approvalAudience.lockAndRevalidateFile(audienceQuery) : approvalAudience.inspectFile(audienceQuery);
            if (grant != null)
                return new FileBusinessObjectPolicyFact(true, grant.approvalId(), "IMMUTABLE", "MULTIPLE",
                        Set.of("CUTOVER_CHECKLIST_ITEM".equals(type) ? "CUTOVER_CHECKLIST_MANUAL_EVIDENCE" : type), MEDIA, 52_428_800L, "INTERNAL");
        }
        var inspected = scopes.inspect(query.actorUserId(), context.task().getProjectId(), action);
        if (!inspected.allowed()) return denied();
        var scope = lock ? scopes.lockAndRevalidate(CutoverProjectScopePort.RevalidationQuery.builder()
                .actorId(query.actorUserId())
                .projectId(context.task().getProjectId())
                .action(action)
                .expectedProjectScopeVersion(expected)
                .build())
                : inspected;
        if (!scope.allowed() || lock && scope.projectScopeVersion() != expected) return denied();
        if (lock) context = locate(query.tenantId(), id, true);
        if (context == null) return denied();
        String suffix = switch (type) {
            case "CUTOVER_CHECKLIST_ITEM" -> "checklist";
            case "CUTOVER_PLAN" -> "plan";
            case "CUTOVER_CLOSURE" -> "closure";
            default -> throw new IllegalArgumentException("unknown CUT file type");
        };
        boolean allowed = writing ? context.editable() && Objects.equals(context.task().getOwnerUserId(), query.actorUserId())
                && permissions.hasAnyPermissions(query.actorUserId(), "pms:cutover-task:save-" + suffix)
                : READ.contains(query.requiredAction()) && permissions.hasAnyPermissions(query.actorUserId(), "pms:cutover-task:query", "pms:cutover-task:query-" + suffix);
        return new FileBusinessObjectPolicyFact(allowed, scope.projectScopeVersion(), context.editable() ? "MUTABLE" : "IMMUTABLE",
                "MULTIPLE", Set.of("CUTOVER_CHECKLIST_ITEM".equals(type) ? "CUTOVER_CHECKLIST_MANUAL_EVIDENCE" : type), MEDIA, 52_428_800L, "INTERNAL");
    }
    private Context locate(Long tenant, Long id, boolean lock) {
        Long taskId = id;
        boolean draft = true;
        Long documentRevisionId = null;
        if ("CUTOVER_CHECKLIST_ITEM".equals(type)) {
            var item = items.selectById(id);
            if (item == null || !Objects.equals(item.getTenantId(), tenant)) return null;
            var checklist = checklists.selectById(item.getChecklistId());
            if (checklist == null || !Objects.equals(checklist.getTenantId(), tenant)) return null;
            taskId = checklist.getCutoverTaskId();
            if (lock) {
                tasks.selectForUpdate(new CutoverTaskRowQuery(tenant, taskId));
                checklist = checklists.selectCurrentForUpdate(new CutoverChecklistRowQuery(tenant, taskId, checklist.getId()));
            }
            draft = checklist != null && "DRAFT".equals(checklist.getStatusCode()) && Boolean.TRUE.equals(item.getApplicableFlag());
            documentRevisionId = checklist == null ? null : checklist.getId();
        } else if ("CUTOVER_CLOSURE".equals(type)) {
            var closure = closures.selectById(id);
            if (closure == null || !Objects.equals(closure.getTenantId(), tenant)) return null;
            taskId = closure.getTaskId();
            if (lock) {
                tasks.selectForUpdate(new CutoverTaskRowQuery(tenant, taskId));
                closure = closures.selectByTaskForUpdate(new CutoverClosureRowQuery(tenant, taskId));
            }
            draft = closure != null && "DRAFT".equals(closure.getStatusCode());
            documentRevisionId = closure == null ? null : closure.getId();
        }
        var task = lock ? tasks.selectForUpdate(new CutoverTaskRowQuery(tenant, taskId)) : tasks.selectById(taskId);
        if (task == null || !Objects.equals(task.getTenantId(), tenant)) return null;
        String stage = switch (type) { case "CUTOVER_CHECKLIST_ITEM" -> "P3"; case "CUTOVER_PLAN" -> "P4"; default -> "P6"; };
        return new Context(task, draft && stage.equals(task.getCurrentStage()) && !"ARCHIVED".equals(task.getTaskStatus()), documentRevisionId);
    }
    private String documentSource(String purpose) {
        return switch (type) {
            case "CUTOVER_PLAN" -> "CUT.PLAN";
            case "CUTOVER_CHECKLIST_ITEM" -> "CUT.CHECKLIST_EVIDENCE";
            case "CUTOVER_CLOSURE" -> switch (purpose) {
                case "POST_COLLECTION_CHECKLIST" -> "CUT.POST_COLLECTION_CHECKLIST";
                case "IMPLEMENTATION_COMMITMENT" -> "CUT.IMPLEMENTATION_COMMITMENT";
                case "OTHER_EVIDENCE" -> "CUT.CLOSURE_EVIDENCE";
                case "MANUAL_COLLECTION_RESULT" -> "CUT.MANUAL_COLLECTION_RESULT";
                default -> throw new IllegalArgumentException("unknown CUT closure file purpose");
            };
            default -> throw new IllegalArgumentException("unknown CUT file type");
        };
    }
    private boolean purposeAllowed(String purpose) {
        return switch (type) {
            case "CUTOVER_CHECKLIST_ITEM" -> "MANUAL_EVIDENCE".equals(purpose);
            case "CUTOVER_PLAN" -> "FULL_PLAN".equals(purpose);
            case "CUTOVER_CLOSURE" -> Set.of("POST_COLLECTION_CHECKLIST", "IMPLEMENTATION_COMMITMENT", "OTHER_EVIDENCE", "MANUAL_COLLECTION_RESULT").contains(purpose);
            default -> false;
        };
    }
    private static FileBusinessObjectPolicyFact denied() { return new FileBusinessObjectPolicyFact(false, null, null, null, Set.of(), Set.of(), null, null); }
    private record Context(CutoverTaskDO task, boolean editable, Long documentRevisionId) {}
}
