package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.query.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalEligibilityPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCompliance;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardCandidate;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.policy.CutoverP5ActionPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.spare.CutoverSpareQueryService;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.*;

/** P5审批只读编排；生产Bean留待Owner依赖接通。 */
public class CutoverApprovalQueryService {
    private static final CutoverP5ActionPolicy ACTION_POLICY = new CutoverP5ActionPolicy();
    private static final CutoverLeadTimeSnapshotCodec LEAD_TIME_CODEC = new CutoverLeadTimeSnapshotCodec();
    private static final int SCAN_SIZE = 100;
    private static final List<String> REVIEW_ORDER = List.of("PREPARATION", "BUSINESS_TEST", "EXECUTION", "ROLLBACK", "OTHER");

    private final CutoverApprovalInstanceMapper instanceMapper;
    private final CutoverApprovalNodeMapper nodeMapper;
    private final CutoverApprovalReviewItemMapper reviewMapper;
    private final CutoverTaskMapper taskMapper;
    private final CutoverApprovalProjectScopePort projectScopePort;
    private final CutoverApprovalSourceSnapshotCodec snapshotCodec;
    private final CutoverApprovalEligibilityPolicy eligibilityPolicy;
    private final CutoverSpareQueryService spareQueryService;

    public CutoverApprovalQueryService(CutoverApprovalInstanceMapper instanceMapper,
            CutoverApprovalNodeMapper nodeMapper, CutoverApprovalReviewItemMapper reviewMapper,
            CutoverTaskMapper taskMapper, ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            CutoverApprovalSourceSnapshotCodec snapshotCodec) {
        this(instanceMapper, nodeMapper, reviewMapper, taskMapper, serviceManagerPort, roleCandidatePort,
                projectScopePort, snapshotCodec, null);
    }

    public CutoverApprovalQueryService(CutoverApprovalInstanceMapper instanceMapper,
            CutoverApprovalNodeMapper nodeMapper, CutoverApprovalReviewItemMapper reviewMapper,
            CutoverTaskMapper taskMapper, ProjectCutoverServiceManagerPort serviceManagerPort,
            CutoverApprovalRoleCandidatePort roleCandidatePort,
            CutoverApprovalProjectScopePort projectScopePort,
            CutoverApprovalSourceSnapshotCodec snapshotCodec,
            CutoverSpareQueryService spareQueryService) {
        this.instanceMapper = instanceMapper; this.nodeMapper = nodeMapper; this.reviewMapper = reviewMapper;
        this.taskMapper = taskMapper; this.projectScopePort = projectScopePort;
        this.snapshotCodec = snapshotCodec;
        this.spareQueryService = spareQueryService;
        this.eligibilityPolicy = new CutoverApprovalEligibilityPolicy(serviceManagerPort, roleCandidatePort,
                projectScopePort, LocalDateTime::now);
    }

    public CutoverApprovalViews.ApprovalView detail(long tenantId, long taskId, long actorId,
                                                     boolean queryPermission, boolean reassignPermission) {
        require(tenantId > 0 && taskId > 0 && actorId > 0, INVALID_REQUEST, "审批详情身份非法");
        CutoverApprovalInstanceDO root = instanceMapper.selectCurrentByTask(new ApprovalTaskQuery(tenantId, taskId));
        require(root != null, STATE_CONFLICT, "审批实例不存在");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), tenantId), STATE_CONFLICT, "审批任务不存在");
        List<CutoverApprovalNodeDO> nodes = nodes(tenantId, root.getId());
        CutoverApprovalNodeDO current = nodes.stream()
                .filter(node -> Objects.equals(node.getNodeNo(), root.getCurrentNodeNo())).findFirst().orElse(null);
        boolean projectView = queryPermission && projectScopePort.inspect(tenantId, root.getProjectId(), actorId,
                "ACTION_VIEW").allowed();
        boolean full = projectView && Objects.equals(root.getInitiatorUserId(), actorId);
        boolean currentEligible = false;
        if (!full && queryPermission && current != null && Objects.equals(current.getCurrentApproverUserId(), actorId))
            full = currentEligible = eligibilityPolicy.eligible(root, current, actorId);
        else if (full && current != null && Objects.equals(current.getCurrentApproverUserId(), actorId))
            currentEligible = eligibilityPolicy.eligible(root, current, actorId);
        if (full) return full(root, task, nodes, current, currentEligible, actorId);
        if (projectView && List.of("APPROVED", "REJECTED").contains(root.getStatusCode())) return finalResult(root);
        if (reassignPermission && "PENDING".equals(root.getStatusCode())) return reassignment(root, task, nodes);
        throw failure(STATE_CONFLICT, "审批详情不可见");
    }

    public CutoverApprovalViews.Page<CutoverApprovalViews.TodoItem> myTodos(long tenantId, long actorId,
                                                                            int pageNo, int pageSize) {
        page(tenantId, actorId, pageNo, pageSize);
        List<CutoverApprovalViews.TodoItem> eligible = new ArrayList<>();
        for (int offset = 0; ; offset += SCAN_SIZE) {
            ApprovalTodoPageQuery query = new ApprovalTodoPageQuery(tenantId, actorId, offset, SCAN_SIZE);
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
        return new CutoverApprovalViews.Page<>(List.copyOf(eligible.subList(from, to)), eligible.size(), pageNo, pageSize);
    }

    public CutoverApprovalViews.Page<CutoverApprovalViews.ReassignmentCandidate> reassignmentCandidates(
            long tenantId, int pageNo, int pageSize) {
        page(tenantId, 1L, pageNo, pageSize);
        ApprovalReassignmentPageQuery query = new ApprovalReassignmentPageQuery(tenantId,
                (pageNo - 1) * pageSize, pageSize);
        List<ApprovalReassignmentPageRow> rows = nodeMapper.selectReassignmentProjectionPage(query);
        return new CutoverApprovalViews.Page<>(rows.stream().map(this::candidate).toList(),
                nodeMapper.countReassignmentCandidates(query), pageNo, pageSize);
    }

    public ReassignmentCommandContext reassignmentCommandContext(long tenantId, long taskId, long actorId) {
        CutoverApprovalViews.ApprovalView view = detail(tenantId, taskId, actorId, false, true);
        require(view instanceof CutoverApprovalViews.ApprovalReassignmentView, STATE_CONFLICT, "审批不可改派");
        CutoverTaskDO task = taskMapper.selectById(taskId);
        require(task != null && Objects.equals(task.getTenantId(), tenantId), OWNER_DATA_CORRUPTED, "审批任务缺失");
        return new ReassignmentCommandContext((CutoverApprovalViews.ApprovalReassignmentView) view, task.getVersion());
    }

    public CutoverApprovalViews.ApprovalDetail decisionResponse(long tenantId, long taskId,
            long approvalInstanceId, int decidedNodeNo, long actorId) {
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

    private CutoverApprovalViews.ApprovalDetail full(CutoverApprovalInstanceDO root, CutoverTaskDO task,
            List<CutoverApprovalNodeDO> nodes, CutoverApprovalNodeDO current, boolean currentEligible, long viewerId) {
        Map<Long, List<CutoverApprovalReviewItemDO>> reviews = reviewMapper.selectList(new LambdaQueryWrapperX<CutoverApprovalReviewItemDO>()
                .eq(CutoverApprovalReviewItemDO::getTenantId, root.getTenantId())
                .eq(CutoverApprovalReviewItemDO::getApprovalInstanceId, root.getId())
                .orderByAsc(CutoverApprovalReviewItemDO::getApprovalNodeId, CutoverApprovalReviewItemDO::getId))
                .stream().collect(java.util.stream.Collectors.groupingBy(CutoverApprovalReviewItemDO::getApprovalNodeId));
        Long actorId = currentEligible && current != null ? current.getCurrentApproverUserId() : null;
        CutoverDashboardCandidate candidate = new CutoverDashboardCandidate(task.getId(), task.getTaskOrigin(),
                task.getCurrentStage(), task.getTaskStatus(), task.getOwnerUserId(), actorId, task.getManualGrade());
        var facts = cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.ActionFacts
                .p5(root.getStatusCode(), current == null ? null : current.getStatusCode(), root.getHoldReasonCode(),
                        current == null ? null : current.getCurrentApproverUserId(), currentEligible);
        List<String> actions = List.copyOf(ACTION_POLICY.allowedActions(candidate, facts,
                cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts
                        .p5()));
        return new CutoverApprovalViews.ApprovalDetail("FULL", root.getId(), root.getVersion(), root.getTaskId(),
                task.getVersion(), root.getPlanRevisionId(), root.getPlanRevisionNo(), root.getGradeCode(),
                root.getStatusCode(), root.getHoldReasonCode(), root.getCurrentNodeNo(),
                nodes.stream().map(node -> node(node, reviews.getOrDefault(node.getId(), List.of()))).toList(),
                snapshotCodec.decode(root.getSourceSnapshot()), leadTime(root), root.getDecisionAt(),
                root.getRejectionReason(), spareQueryService == null ? null
                        : spareQueryService.approvalSummary(root.getTenantId(), root.getTaskId(), viewerId), actions);
    }

    private static CutoverLeadTimeCompliance leadTime(CutoverApprovalInstanceDO root) {
        if (Boolean.FALSE.equals(root.getLeadTimeEnabled())) {
            require(root.getLeadTimeSnapshot() == null, OWNER_DATA_CORRUPTED, "禁用提前时间的审批根包含快照");
            return null;
        }
        require(Boolean.TRUE.equals(root.getLeadTimeEnabled()) && List.of("A", "B").contains(root.getGradeCode())
                        && root.getLeadTimeSnapshot() != null,
                OWNER_DATA_CORRUPTED, "审批提前时间根字段损坏");
        try {
            return LEAD_TIME_CODEC.decode(root.getLeadTimeSnapshot());
        } catch (IllegalArgumentException exception) {
            throw failure(OWNER_DATA_CORRUPTED, "审批提前时间快照损坏");
        }
    }

    private List<CutoverApprovalNodeDO> nodes(long tenantId, long instanceId) {
        return nodeMapper.selectList(new LambdaQueryWrapperX<CutoverApprovalNodeDO>()
                .eq(CutoverApprovalNodeDO::getTenantId, tenantId)
                .eq(CutoverApprovalNodeDO::getApprovalInstanceId, instanceId)
                .orderByAsc(CutoverApprovalNodeDO::getNodeNo));
    }

    private CutoverApprovalViews.Node node(CutoverApprovalNodeDO row, List<CutoverApprovalReviewItemDO> reviews) {
        List<CutoverApprovalViews.ReviewItem> items = reviews.stream()
                .sorted(Comparator.comparingInt(value -> REVIEW_ORDER.indexOf(value.getItemCode())))
                .map(value -> new CutoverApprovalViews.ReviewItem(value.getItemCode(), value.getDecisionCode(),
                        value.getUnreasonableReason())).toList();
        CutoverApprovalViews.AssessmentReview assessment = row.getAssessmentReviewDecisionCode() == null ? null
                : new CutoverApprovalViews.AssessmentReview(row.getAssessmentReviewDecisionCode(), row.getAssessmentReviewReason());
        return new CutoverApprovalViews.Node(row.getId(), row.getNodeNo(), row.getNodeCode(), row.getStatusCode(),
                row.getOriginalApproverUserId(), row.getCurrentApproverUserId(), row.getDecisionAt(), row.getFeedback(), items, assessment);
    }

    private CutoverApprovalViews.ApprovalFinalResult finalResult(CutoverApprovalInstanceDO root) {
        return new CutoverApprovalViews.ApprovalFinalResult("FINAL_RESULT_ONLY", root.getId(), root.getTaskId(),
                root.getPlanRevisionId(), root.getGradeCode(), root.getStatusCode(), root.getDecisionAt(),
                root.getRejectionReason(), List.of());
    }

    private CutoverApprovalViews.ApprovalReassignmentView reassignment(CutoverApprovalInstanceDO root,
            CutoverTaskDO task, List<CutoverApprovalNodeDO> nodes) {
        List<CutoverApprovalViews.ReassignmentNode> open = nodes.stream()
                .filter(node -> List.of("WAITING", "PENDING").contains(node.getStatusCode()))
                .map(node -> new CutoverApprovalViews.ReassignmentNode(node.getId(), node.getNodeNo(), node.getNodeCode(),
                        node.getStatusCode(), node.getCurrentApproverUserId(), node.getVersion())).toList();
        return new CutoverApprovalViews.ApprovalReassignmentView("REASSIGNMENT_ONLY", root.getId(), root.getVersion(),
                task.getId(), root.getProjectId(), task.getTaskNo(), task.getTaskName(), root.getGradeCode(),
                root.getStatusCode(), root.getHoldReasonCode(), open, List.of("REASSIGN"));
    }

    private CutoverApprovalViews.TodoItem todo(ApprovalTodoPageRow row) {
        return new CutoverApprovalViews.TodoItem(row.getApprovalInstanceId(), row.getApprovalVersion(), row.getTaskId(),
                row.getProjectId(), row.getTaskCode(), row.getTaskName(), row.getGrade(), row.getNodeNo(),
                row.getNodeCode(), row.getCreatedAt());
    }
    private CutoverApprovalViews.ReassignmentCandidate candidate(ApprovalReassignmentPageRow row) {
        return new CutoverApprovalViews.ReassignmentCandidate(row.getApprovalInstanceId(), row.getApprovalVersion(),
                row.getTaskId(), row.getProjectId(), row.getTaskCode(), row.getTaskName(), row.getGrade(), row.getStatus(),
                row.getHoldReason(), row.getNodeId(), row.getNodeNo(), row.getNodeCode(), row.getNodeStatus(),
                row.getCurrentApproverUserId(), row.getNodeVersion(), row.getCreatedAt());
    }
    private static void page(long tenantId, long actorId, int pageNo, int pageSize) {
        require(tenantId > 0 && actorId > 0 && pageNo > 0 && pageSize > 0 && pageSize <= 100,
                INVALID_REQUEST, "审批分页参数非法");
    }
    private static void require(boolean condition, CutoverApprovalApplicationException.Code code, String message) {
        if (!condition) throw failure(code, message);
    }
    private static CutoverApprovalApplicationException failure(CutoverApprovalApplicationException.Code code, String message) {
        return new CutoverApprovalApplicationException(code, message);
    }

    public record ReassignmentCommandContext(CutoverApprovalViews.ApprovalReassignmentView view,
                                             Integer taskVersion) { }
}
