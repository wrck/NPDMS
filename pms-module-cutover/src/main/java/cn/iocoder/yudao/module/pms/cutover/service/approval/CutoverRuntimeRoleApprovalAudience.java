package cn.iocoder.yudao.module.pms.cutover.service.approval;

import jakarta.annotation.Resource;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverRuntimeApprovalNodeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalEligibilityPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeRoleApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 当前角色待办只授予对应审批摘要和冻结来源的只读访问。 */
@Service
public class CutoverRuntimeRoleApprovalAudience {
    @Resource
    private CutoverApprovalStorage storage;

    private final CutoverTaskMapper tasks;
    private final CutoverApprovalInstanceMapper instances;
    private final CutoverRuntimeApprovalNodeMapper nodes;
    private final CutoverApprovalRoleCandidatePort roles;
    private final PermissionApi permissions;
    private final FileEvidenceApi evidence;
    private final CutoverRuntimeApprovalEligibilityPolicy eligibility;

    public CutoverRuntimeRoleApprovalAudience(CutoverTaskMapper tasks, CutoverApprovalInstanceMapper instances,
            CutoverRuntimeApprovalNodeMapper nodes, ProjectCutoverRuntimeServiceManagerPort managers,
            CutoverApprovalRoleCandidatePort roles, CutoverApprovalProjectScopePort scopes, PermissionApi permissions,
            FileEvidenceApi evidence) {
        this.tasks = tasks; this.instances = instances; this.nodes = nodes;
        this.roles = roles; this.permissions = permissions;
        this.evidence = evidence;
        this.eligibility = new CutoverRuntimeApprovalEligibilityPolicy(managers, roles, scopes, LocalDateTime::now);
    }

    public Grant inspect(Query query) {
        if (!taskAllowed(query)) return null;
        var root = instances.selectCurrentByTask(new ApprovalTaskQuery(query.tenantId(), query.taskId()));
        if (root == null || root.getReplacementApprovalInstanceId() != null) return null;
        return qualified(query, root, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public Grant inspectFile(FileQuery query) {
        if (!taskAllowed(query.access())) return null;
        for (var root : instances.history(new ApprovalTaskQuery(query.access().tenantId(), query.access().taskId()))) {
            var grant = qualified(query.access(), root, false);
            if (grant != null && frozenFileAvailable(query, grant)) return grant;
        }
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    public Grant lockAndRevalidateFile(FileQuery query) {
        if (query.access().expectedApprovalId() == null || !taskAllowed(query.access())) return null;
        var root = instances.selectByIdForUpdate(new ApprovalInstanceLockQuery(query.access().tenantId(),
                query.access().expectedApprovalId(), null, null));
        var grant = qualified(query.access(), root, true);
        return grant != null && frozenFileAvailable(query, grant) ? grant : null;
    }

    private boolean frozenFileAvailable(FileQuery query, Grant grant) {
        var file = grant.selectedFile(query.reference());
        if (file == null) return false;
        String purpose = "CUTOVER_PLAN".equals(query.reference().objectType()) ? "FULL_PLAN" : "MANUAL_EVIDENCE";
        var fact = evidence.lockAndRevalidate(new FileEvidenceApi.Query(query.access().tenantId(), file.artifactId(),
                file.versionNo(), "CUT", query.reference().objectType(), query.reference().objectId(), purpose,
                file.referenceKey(), file.sha256()));
        return fact != null && fact.valid();
    }

    public Set<Long> authorizedTaskIds(Selection query) {
        return query.taskIds().stream().filter(task -> inspect(new Query(query.tenantId(), query.actorId(), task, null)) != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean taskAllowed(Query query) {
        if (!permissions.hasAnyPermissions(query.actorId(), "pms:cutover-task:query-approval")) return false;
        var task = tasks.selectById(query.taskId());
        return task != null && Objects.equals(task.getTenantId(), query.tenantId());
    }

    private Grant qualified(Query query, cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO root, boolean lock) {
        if (root == null || !Objects.equals(root.getTenantId(), query.tenantId())
                || !Objects.equals(root.getTaskId(), query.taskId())
                || query.expectedApprovalId() != null && !Objects.equals(root.getId(), query.expectedApprovalId())) return null;
        for (var node : nodes.all(new CutoverApprovalNodesQuery(query.tenantId(), root.getId()))) {
            var snapshot = CutoverRuntimeRoleApprovalSnapshot.from(node);
            if (!CutoverRuntimeRoleApprovalSnapshot.currentRoleTodo(root, node)
                    || !eligibility.eligible(root, node, query.actorId())) continue;
            if (lock) {
                var actorQuery = new CutoverApprovalRoleCandidatePort.ActorQuery(query.tenantId(), query.actorId());
                if (snapshot.currentActor(actorQuery, roles.lockAndInspectActor(actorQuery)) == null) return null;
            }
            return new Grant(root.getId(), JsonUtils.toJsonString(storage.sourceView(root)));
        }
        return null;
    }

    public record Query(Long tenantId, Long actorId, Long taskId, Long expectedApprovalId) { }
    public record Selection(Long tenantId, Long actorId, Set<Long> taskIds) {
        public Selection { taskIds = Set.copyOf(taskIds); }
    }
    public record Grant(Long approvalId, String sourceSnapshot) {
        public boolean references(FileReference query) {
            return selectedFile(query) != null;
        }
        public CutoverPlanFilePort.FileFact selectedFile(FileReference query) {
            JsonNode frozen = JsonUtils.parseTree(sourceSnapshot);
            if ("CUTOVER_PLAN".equals(query.objectType())) {
                var plan = frozen.path("plan");
                var value = findReference(plan.path("content"), query.referenceKey());
                return value == null ? null : JsonUtils.parseObject(JsonUtils.toJsonString(value), CutoverPlanFilePort.FileFact.class);
            }
            if (!"CUTOVER_CHECKLIST_ITEM".equals(query.objectType())) return null;
            for (String group : new String[]{"riskItems", "businessSurveyItems"}) for (var item : frozen.path(group)) {
                if (query.objectId().equals(item.path("checklistItemId").asText())) {
                    var answer = JsonUtils.parseTree(item.path("answerSnapshot").asText());
                    var value = findReference(answer, query.referenceKey());
                    return value == null ? null : JsonUtils.parseObject(JsonUtils.toJsonString(value), CutoverPlanFilePort.FileFact.class);
                }
            }
            return null;
        }
        private static JsonNode findReference(JsonNode value, String key) {
            if (value == null) return null;
            if (value.isObject() && value.has("artifactId") && value.has("versionNo")
                    && key.equals(value.path("referenceKey").asText())) return value;
            if (value.isObject() || value.isArray()) for (var child : value) {
                var match = findReference(child, key);
                if (match != null) return match;
            }
            return null;
        }
    }
    public record FileReference(String objectType, String objectId, String referenceKey) { }
    public record FileQuery(Query access, FileReference reference) { }
}
