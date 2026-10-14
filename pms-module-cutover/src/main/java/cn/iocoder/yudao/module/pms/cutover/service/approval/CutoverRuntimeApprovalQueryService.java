package cn.iocoder.yudao.module.pms.cutover.service.approval;

import jakarta.annotation.Resource;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import cn.iocoder.yudao.module.pms.cutover.service.approval.result.CutoverApprovalDecisionResult;

import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.CutoverApprovalNodesQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverRuntimeApprovalViews;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeApprovalEligibilityPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverRuntimeRoleApprovalSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCompliance;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP5ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.spare.CutoverSpareQueryService;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.*;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.*;

/** P5 审批只读编排，读取冻结版本与当前候选资格。 */
public class CutoverRuntimeApprovalQueryService {
    private static final CutoverP5ActionPolicy ACTION_POLICY = new CutoverP5ActionPolicy();
    private static final int SCAN_SIZE = 100;
    private static final List<String> REVIEW_ORDER = List.of("PREPARATION", "BUSINESS_TEST", "EXECUTION", "ROLLBACK", "OTHER");

    @Resource
    private CutoverApprovalStorage storage;

    private final CutoverApprovalRoleCandidatePort roleCandidatePort;
    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverRuntimeApprovalNodeMapper nodeMapper;
    private final CutoverRuntimeApprovalReviewItemMapper reviewMapper;
    private final CutoverTaskMapper taskMapper;
    private final CutoverApprovalProjectScopePort projectScopePort;
    private final CutoverRuntimeApprovalSourceSnapshotCodec snapshotCodec;
    private final CutoverRuntimeApprovalEligibilityPolicy eligibilityPolicy;
    private final CutoverSpareQueryService spareQueryService;

    public CutoverRuntimeApprovalQueryService(CutoverApprovalInstanceMapper instanceMapper,
            CutoverRuntimeApprovalNodeMapper nodeMapper, CutoverRuntimeApprovalReviewItemMapper reviewMapper,
            CutoverTaskMapper taskMapper, ProjectCutoverRuntimeServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            CutoverRuntimeApprovalSourceSnapshotCodec snapshotCodec) {
        this(instanceMapper, nodeMapper, reviewMapper, taskMapper, serviceManagerPort, roleCandidatePort,
                projectScopePort, snapshotCodec, null);
    }

    public CutoverRuntimeApprovalQueryService(CutoverApprovalInstanceMapper instanceMapper,
            CutoverRuntimeApprovalNodeMapper nodeMapper, CutoverRuntimeApprovalReviewItemMapper reviewMapper,
            CutoverTaskMapper taskMapper, ProjectCutoverRuntimeServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            CutoverRuntimeApprovalSourceSnapshotCodec snapshotCodec,
            CutoverSpareQueryService spareQueryService) {
        this.instanceMapper = instanceMapper; this.nodeMapper = nodeMapper; this.reviewMapper = reviewMapper;
        this.taskMapper = taskMapper; this.projectScopePort = projectScopePort;
        this.roleCandidatePort = roleCandidatePort;
        this.snapshotCodec = snapshotCodec;
        this.spareQueryService = spareQueryService;
        this.eligibilityPolicy = new CutoverRuntimeApprovalEligibilityPolicy(serviceManagerPort, roleCandidatePort,
                projectScopePort, LocalDateTime::now);
    }

    public CutoverRuntimeApprovalViews.ApprovalView detail(CutoverApprovalContext context, long taskId) {
        long tenantId = context.tenantId(); long actorId = context.actorId();
        require(tenantId > 0 && taskId > 0 && actorId > 0, INVALID_REQUEST, "审批详情身份非法");
        CutoverApprovalInstanceDO root = instanceMapper.selectCurrentByTask(new ApprovalTaskQuery(tenantId, taskId));
        require(root != null, STATE_CONFLICT, "审批实例不存在");
        return detail(root, context);
    }

    public CutoverRuntimeApprovalViews.ApprovalView detailByInstance(CutoverApprovalContext context, long approvalId) {
        long tenantId = context.tenantId(); long actorId = context.actorId();
        require(tenantId > 0 && approvalId > 0 && actorId > 0, INVALID_REQUEST, "审批详情身份非法");
        var root = instanceMapper.selectById(approvalId);
        require(root != null && Objects.equals(root.getTenantId(), tenantId), STATE_CONFLICT, "审批实例不存在");
        return detail(root, context);
    }

    private CutoverRuntimeApprovalViews.ApprovalView detail(CutoverApprovalInstanceDO root, CutoverApprovalContext context) {
        long actorId = context.actorId();
        long tenantId = root.getTenantId(); long taskId = root.getTaskId();
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), tenantId), STATE_CONFLICT, "审批任务不存在");
        List<CutoverApprovalNodeDO> nodes = nodes(tenantId, root.getId());
        CutoverApprovalNodeDO current = nodes.stream()
                .filter(node -> Objects.equals(node.getNodeNo(), root.getCurrentNodeNo())).findFirst().orElse(null);
        boolean roleView = context.canQuery() && CutoverRuntimeRoleApprovalSnapshot.currentRoleTodo(root, current)
                && eligibilityPolicy.eligible(root, current, actorId);
        boolean projectView = !roleView && context.canQuery() && projectScopePort.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(tenantId)
                .projectId(root.getProjectId())
                .subjectUserId(actorId)
                .requiredAction("ACTION_VIEW")
                .build()).allowed();
        boolean full = roleView || projectView && Objects.equals(root.getInitiatorUserId(), actorId);
        boolean currentEligible = false;
        var currentRole = CutoverRuntimeRoleApprovalSnapshot.from(current);
        boolean candidate = current != null && (currentRole != null && currentRole.byRole()
                ? !currentRole.tenantRole() || CutoverRuntimeRoleApprovalSnapshot.currentRoleTodo(root, current)
                : Objects.equals(current.getCurrentApproverUserId(), actorId));
        if (!full && context.canQuery() && candidate)
            full = currentEligible = eligibilityPolicy.eligible(root, current, actorId);
        else if (full && candidate)
            currentEligible = eligibilityPolicy.eligible(root, current, actorId);
        if (full) return full(root, task, nodes, current, currentEligible, actorId);
        if (projectView && List.of("APPROVED", "REJECTED").contains(root.getStatusCode())) return finalResult(root);
        if (context.canReassign() && "PENDING".equals(root.getStatusCode())) return reassignment(root, task, nodes);
        throw failure(STATE_CONFLICT, "审批详情不可见");
    }

    public CutoverRuntimeApprovalViews.Page<CutoverRuntimeApprovalViews.TodoItem> myTodos(CutoverApprovalContext context, PageParam pagination) {
        page(context, pagination);
        long tenantId = context.tenantId(); long actorId = context.actorId();
        int pageNo = pagination.getPageNo(); int pageSize = pagination.getPageSize();
        Set<Long> candidateRoleIds = roleCandidatePort.inspectActor(
                new CutoverApprovalRoleCandidatePort.ActorQuery(tenantId, actorId)).stream()
                .filter(member -> member.role().tenantId() == tenantId && member.candidate().adminUserId() == actorId)
                .filter(member -> Set.of("probSupporter", "probRD").contains(member.role().roleGroupCode()))
                .map(member -> member.role().roleId()).collect(java.util.stream.Collectors.toSet());
        List<CutoverRuntimeApprovalViews.TodoItem> eligible = new ArrayList<>();
        for (int offset = 0; ; offset += SCAN_SIZE) {
            ApprovalTodoPageQuery query = ApprovalTodoPageQuery.builder().tenantId(tenantId).currentUserId(actorId)
                    .offset(offset).pageSize(SCAN_SIZE).candidateRoleIds(candidateRoleIds).build();
            List<CutoverApprovalNodeDO> nodes = nodeMapper.selectTodoPage(query);
            List<ApprovalTodoPageRow> rows = nodeMapper.selectTodoProjectionPage(query);
            require(nodes.size() == rows.size(), OWNER_DATA_CORRUPTED, "待办投影身份损坏");
            for (int index = 0; index < nodes.size(); index++) {
                ApprovalTodoPageRow row = rows.get(index);
                CutoverApprovalNodeDO node = nodes.get(index);
                require(Objects.equals(row.getNodeId(), node.getId())
                                && Objects.equals(row.getApprovalInstanceId(), node.getApprovalInstanceId())
                                && Objects.equals(row.getNodeNo(), node.getNodeNo())
                                && Objects.equals(row.getNodeCode(), node.getNodeCode()),
                        OWNER_DATA_CORRUPTED, "待办节点投影身份损坏");
                CutoverApprovalInstanceDO root = instanceMapper.selectById(row.getApprovalInstanceId());
                require(root != null && Objects.equals(root.getTenantId(), tenantId), OWNER_DATA_CORRUPTED, "待办审批根缺失");
                if (eligibilityPolicy.eligible(root, node, actorId)) eligible.add(todo(row));
            }
            if (nodes.size() < SCAN_SIZE) break;
        }
        int from = Math.min((pageNo - 1) * pageSize, eligible.size());
        int to = Math.min(from + pageSize, eligible.size());
        return new CutoverRuntimeApprovalViews.Page<>(List.copyOf(eligible.subList(from, to)), eligible.size(), pageNo, pageSize);
    }

    public CutoverRuntimeApprovalViews.Page<CutoverRuntimeApprovalViews.ReassignmentCandidate> reassignmentCandidates(
            CutoverApprovalContext context, PageParam pagination) {
        page(context, pagination);
        long tenantId = context.tenantId();
        int pageNo = pagination.getPageNo(); int pageSize = pagination.getPageSize();
        ApprovalReassignmentPageQuery query = new ApprovalReassignmentPageQuery(tenantId,
                (pageNo - 1) * pageSize, pageSize);
        List<ApprovalReassignmentPageRow> rows = nodeMapper.selectReassignmentProjectionPage(query);
        return new CutoverRuntimeApprovalViews.Page<>(rows.stream().map(this::candidate).toList(),
                nodeMapper.countReassignmentCandidates(query), pageNo, pageSize);
    }

    public ReassignmentCommandContext reassignmentCommandContext(CutoverApprovalContext context, long taskId) {
        long tenantId = context.tenantId();
        var reassignmentAccess = new CutoverApprovalContext(tenantId, context.actorId(), context.correlationId(),
                false, false, context.canReassign());
        CutoverRuntimeApprovalViews.ApprovalView view = detail(reassignmentAccess, taskId);
        require(view instanceof CutoverRuntimeApprovalViews.ApprovalReassignmentView, STATE_CONFLICT, "审批不可改派");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), tenantId), OWNER_DATA_CORRUPTED, "审批任务缺失");
        return new ReassignmentCommandContext((CutoverRuntimeApprovalViews.ApprovalReassignmentView) view, task.getVersion());
    }

    public CutoverRuntimeApprovalViews.ApprovalDetail decisionResponse(CutoverApprovalContext context,
            CutoverApprovalDecisionResult result) {
        long tenantId = context.tenantId(); long actorId = context.actorId();
        long taskId = result.taskId(); long approvalInstanceId = result.approvalInstanceId();
        int decidedNodeNo = result.decidedNodeNo();
        require(tenantId > 0 && taskId > 0 && approvalInstanceId > 0 && decidedNodeNo > 0 && actorId > 0,
                INVALID_REQUEST, "审批结果身份非法");
        CutoverApprovalInstanceDO root = instanceMapper.selectById(approvalInstanceId);
        require(root != null && Objects.equals(root.getTenantId(), tenantId)
                && Objects.equals(root.getTaskId(), taskId), OWNER_DATA_CORRUPTED, "审批结果根损坏");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), tenantId), OWNER_DATA_CORRUPTED, "审批结果任务损坏");
        List<CutoverApprovalNodeDO> nodes = nodes(tenantId, approvalInstanceId);
        CutoverApprovalNodeDO decided = nodes.stream().filter(node -> Objects.equals(node.getNodeNo(), decidedNodeNo))
                .findFirst().orElseThrow(() -> failure(OWNER_DATA_CORRUPTED, "审批结果节点缺失"));
        require(Objects.equals(decided.getCurrentApproverUserId(), actorId)
                && List.of("APPROVED", "REJECTED").contains(decided.getStatusCode()),
                OWNER_DATA_CORRUPTED, "审批结果节点身份损坏");
        CutoverApprovalNodeDO current = nodes.stream()
                .filter(node -> Objects.equals(node.getNodeNo(), root.getCurrentNodeNo())).findFirst().orElse(null);
        return full(root, task, nodes, current, false, actorId);
    }

    private CutoverRuntimeApprovalViews.ApprovalDetail full(CutoverApprovalInstanceDO root, CutoverTaskDO task,
            List<CutoverApprovalNodeDO> nodes, CutoverApprovalNodeDO current, boolean currentEligible, long viewerId) {
        Map<Long, List<CutoverApprovalReviewItemDO>> reviews = reviewMapper.byInstance(new CutoverApprovalNodesQuery(root.getTenantId(), root.getId()))
                .stream().collect(java.util.stream.Collectors.groupingBy(CutoverApprovalReviewItemDO::getApprovalNodeId));
        Long actorId = currentEligible && current != null ? viewerId : null;
        CutoverDashboardCandidate candidate = new CutoverDashboardCandidate(task.getId(), task.getTaskOrigin(),
                task.getCurrentStage(), task.getTaskStatus(), task.getOwnerUserId(), actorId, task.getManualGrade());
        var facts = cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts
                .p5(root.getStatusCode(), current == null ? null : current.getStatusCode(), root.getHoldReasonCode(),
                        current == null ? null : CutoverRuntimeRoleApprovalSnapshot.isRole(current)
                                ? actorId : current.getCurrentApproverUserId(), currentEligible);
        List<String> actions = List.copyOf(ACTION_POLICY.allowedActions(candidate, facts,
                cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts
                        .p5()));
        return new CutoverRuntimeApprovalViews.ApprovalDetail("FULL", root.getId(), root.getVersion(), root.getTaskId(),
                task.getVersion(), root.getPlanRevisionId(), root.getPlanRevisionNo(), root.getGradeCode(),
                root.getStatusCode(), root.getHoldReasonCode(), root.getCurrentNodeNo(),
                nodes.stream().map(node -> node(node, reviews.getOrDefault(node.getId(), List.of()))).toList(),
                snapshotCodec.decode(storage.sourceView(root)), leadTime(root), root.getDecisionAt(),
                root.getRejectionReason(), spareQueryService == null ? null
                        : spareQueryService.approvalSummary(root.getTenantId(), root.getTaskId(), viewerId), actions);
    }

    private static CutoverLeadTimeCompliance leadTime(CutoverApprovalInstanceDO root) {
        try {
            return CutoverApprovalStorage.leadTime(root);
        } catch (IllegalArgumentException exception) {
            throw failure(OWNER_DATA_CORRUPTED, "审批提前时间快照损坏");
        }
    }

    private List<CutoverApprovalNodeDO> nodes(long tenantId, long instanceId) {
        return nodeMapper.all(new CutoverApprovalNodesQuery(tenantId, instanceId));
    }

    private CutoverRuntimeApprovalViews.Node node(CutoverApprovalNodeDO row, List<CutoverApprovalReviewItemDO> reviews) {
        List<CutoverRuntimeApprovalViews.ReviewItem> items = reviews.stream()
                .sorted(Comparator.comparingInt(value -> REVIEW_ORDER.indexOf(value.getItemCode())))
                .map(value -> new CutoverRuntimeApprovalViews.ReviewItem(value.getItemCode(), value.getDecisionCode(),
                        value.getUnreasonableReason())).toList();
        CutoverRuntimeApprovalViews.AssessmentReview assessment = row.getAssessmentReviewDecisionCode() == null ? null
                : new CutoverRuntimeApprovalViews.AssessmentReview(row.getAssessmentReviewDecisionCode(), row.getAssessmentReviewReason());
        return new CutoverRuntimeApprovalViews.Node(row.getId(), row.getNodeNo(), row.getNodeCode(), row.getStatusCode(),
                row.getOriginalApproverUserId(), row.getCurrentApproverUserId(), row.getDecisionAt(), row.getFeedback(), items, assessment,
                CutoverRuntimeRoleApprovalSnapshot.roleCode(row));
    }

    private CutoverRuntimeApprovalViews.ApprovalFinalResult finalResult(CutoverApprovalInstanceDO root) {
        return new CutoverRuntimeApprovalViews.ApprovalFinalResult("FINAL_RESULT_ONLY", root.getId(), root.getTaskId(),
                root.getPlanRevisionId(), root.getGradeCode(), root.getStatusCode(), root.getDecisionAt(),
                root.getRejectionReason(), List.of());
    }

    private CutoverRuntimeApprovalViews.ApprovalReassignmentView reassignment(CutoverApprovalInstanceDO root,
            CutoverTaskDO task, List<CutoverApprovalNodeDO> nodes) {
        List<CutoverRuntimeApprovalViews.ReassignmentNode> open = nodes.stream()
                .filter(node -> List.of("WAITING", "PENDING").contains(node.getStatusCode())
                        && !CutoverRuntimeRoleApprovalSnapshot.isRole(node))
                .map(node -> new CutoverRuntimeApprovalViews.ReassignmentNode(node.getId(), node.getNodeNo(), node.getNodeCode(),
                        node.getStatusCode(), node.getCurrentApproverUserId(), node.getVersion())).toList();
        return new CutoverRuntimeApprovalViews.ApprovalReassignmentView("REASSIGNMENT_ONLY", root.getId(), root.getVersion(),
                task.getId(), root.getProjectId(), task.getTaskNo(), task.getTaskName(), root.getGradeCode(),
                root.getStatusCode(), root.getHoldReasonCode(), open, List.of("REASSIGN"));
    }

    private CutoverRuntimeApprovalViews.TodoItem todo(ApprovalTodoPageRow row) {
        return new CutoverRuntimeApprovalViews.TodoItem(row.getApprovalInstanceId(), row.getApprovalVersion(), row.getTaskId(),
                row.getProjectId(), row.getTaskCode(), row.getTaskName(), row.getGrade(), row.getNodeNo(),
                row.getNodeCode(), row.getCreatedAt());
    }
    private CutoverRuntimeApprovalViews.ReassignmentCandidate candidate(ApprovalReassignmentPageRow row) {
        return new CutoverRuntimeApprovalViews.ReassignmentCandidate(row.getApprovalInstanceId(), row.getApprovalVersion(),
                row.getTaskId(), row.getProjectId(), row.getTaskCode(), row.getTaskName(), row.getGrade(), row.getStatus(),
                row.getHoldReason(), row.getNodeId(), row.getNodeNo(), row.getNodeCode(), row.getNodeStatus(),
                row.getCurrentApproverUserId(), row.getNodeVersion(), row.getCreatedAt());
    }
    private static void page(CutoverApprovalContext context, PageParam pagination) {
        require(context.tenantId() > 0 && context.actorId() > 0 && pagination.getPageNo() > 0
                        && pagination.getPageSize() > 0 && pagination.getPageSize() <= 100,
                INVALID_REQUEST, "审批分页参数非法");
    }
    private static void require(boolean condition, CutoverApprovalApplicationException.Code code, String message) {
        if (!condition) throw failure(code, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code, String message) {
        return new CutoverApprovalApplicationException(code, message);
    }

    public record ReassignmentCommandContext(CutoverRuntimeApprovalViews.ApprovalReassignmentView view,
                                             Long taskVersion) { }
}
