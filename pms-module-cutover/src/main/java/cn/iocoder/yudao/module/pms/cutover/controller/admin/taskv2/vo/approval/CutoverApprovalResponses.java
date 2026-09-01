package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public final class CutoverApprovalResponses {
    private CutoverApprovalResponses() { }

    public sealed interface View permits Detail, FinalResult, Reassignment { }
    public record ReviewItem(String itemCode, String decision, String unreasonableReason) { }
    public record AssessmentReview(String decision, String reason) { }
    public record Node(Long nodeId, Integer nodeNo, String nodeCode, String status,
                       Long originalApproverUserId, Long currentApproverUserId, Long decisionAt,
                       String feedback, List<ReviewItem> reviewItems, AssessmentReview assessmentReview) { }
    public record Detail(String viewMode, Long approvalInstanceId, Integer approvalVersion, Long taskId,
                         Integer taskVersion, Long planRevisionId, Integer planRevisionNo, String grade,
                         String status, String holdReason, Integer currentNodeNo, List<Node> nodes,
                         CutoverApprovalSourceSnapshotCodec.ApprovalSourceSnapshot sourceSnapshot,
                         Long decisionAt, String rejectionReason, List<String> allowedActions) implements View { }
    public record FinalResult(String viewMode, Long approvalInstanceId, Long taskId, Long planRevisionId,
                              String grade, String status, Long decisionAt, String rejectionReason,
                              List<String> allowedActions) implements View { }
    public record ReassignmentNode(Long nodeId, Integer nodeNo, String nodeCode, String nodeStatus,
                                   Long currentApproverUserId, Integer nodeVersion) { }
    public record Reassignment(String viewMode, Long approvalInstanceId, Integer approvalVersion, Long taskId,
                               Long projectId, String taskCode, String taskName, String grade, String status,
                               String holdReason, List<ReassignmentNode> nodes,
                               List<String> allowedActions) implements View { }
    public record TodoItem(Long approvalInstanceId, Integer approvalVersion, Long taskId, Long projectId,
                           String taskCode, String taskName, String grade, Integer nodeNo, String nodeCode,
                           Long createdAt) { }
    public record TodoPage(List<TodoItem> list, Long total, Integer pageNo, Integer pageSize) { }
    public record ReassignmentCandidate(Long approvalInstanceId, Integer approvalVersion, Long taskId,
                                        Long projectId, String taskCode, String taskName, String grade,
                                        String status, String holdReason, Long nodeId, Integer nodeNo,
                                        String nodeCode, String nodeStatus, Long currentApproverUserId,
                                        Integer nodeVersion, Long createdAt) { }
    public record ReassignmentCandidatePage(List<ReassignmentCandidate> list, Long total,
                                             Integer pageNo, Integer pageSize) { }

    public static View view(CutoverApprovalViews.ApprovalView source) {
        return switch (source) {
            case CutoverApprovalViews.ApprovalDetail value -> new Detail(value.viewMode(), value.approvalInstanceId(),
                    value.approvalVersion(), value.taskId(), value.taskVersion(), value.planRevisionId(),
                    value.planRevisionNo(), value.grade(), value.status(), value.holdReason(), value.currentNodeNo(),
                    value.nodes().stream().map(CutoverApprovalResponses::node).toList(), value.sourceSnapshot(),
                    epoch(value.decisionAt()), value.rejectionReason(), value.allowedActions());
            case CutoverApprovalViews.ApprovalFinalResult value -> new FinalResult(value.viewMode(),
                    value.approvalInstanceId(), value.taskId(), value.planRevisionId(), value.grade(), value.status(),
                    epoch(value.decisionAt()), value.rejectionReason(), value.allowedActions());
            case CutoverApprovalViews.ApprovalReassignmentView value -> reassignment(value);
        };
    }

    public static Reassignment reassignment(CutoverApprovalViews.ApprovalReassignmentView value) {
        return new Reassignment(value.viewMode(), value.approvalInstanceId(), value.approvalVersion(), value.taskId(),
                value.projectId(), value.taskCode(), value.taskName(), value.grade(), value.status(), value.holdReason(),
                value.nodes().stream().map(node -> new ReassignmentNode(node.nodeId(), node.nodeNo(), node.nodeCode(),
                        node.nodeStatus(), node.currentApproverUserId(), node.nodeVersion())).toList(),
                value.allowedActions());
    }

    public static TodoPage todos(CutoverApprovalViews.Page<CutoverApprovalViews.TodoItem> page) {
        return new TodoPage(page.list().stream().map(item -> new TodoItem(item.approvalInstanceId(),
                item.approvalVersion(), item.taskId(), item.projectId(), item.taskCode(), item.taskName(), item.grade(),
                item.nodeNo(), item.nodeCode(), epoch(item.createdAt()))).toList(), page.total(), page.pageNo(), page.pageSize());
    }

    public static ReassignmentCandidatePage candidates(
            CutoverApprovalViews.Page<CutoverApprovalViews.ReassignmentCandidate> page) {
        return new ReassignmentCandidatePage(page.list().stream().map(item -> new ReassignmentCandidate(
                item.approvalInstanceId(), item.approvalVersion(), item.taskId(), item.projectId(), item.taskCode(),
                item.taskName(), item.grade(), item.status(), item.holdReason(), item.nodeId(), item.nodeNo(),
                item.nodeCode(), item.nodeStatus(), item.currentApproverUserId(), item.nodeVersion(),
                epoch(item.createdAt()))).toList(), page.total(), page.pageNo(), page.pageSize());
    }

    private static Node node(CutoverApprovalViews.Node value) {
        return new Node(value.nodeId(), value.nodeNo(), value.nodeCode(), value.status(),
                value.originalApproverUserId(), value.currentApproverUserId(), epoch(value.decisionAt()),
                value.feedback(), value.reviewItems().stream().map(item -> new ReviewItem(
                item.itemCode(), item.decision(), item.unreasonableReason())).toList(),
                value.assessmentReview() == null ? null : new AssessmentReview(
                        value.assessmentReview().decision(), value.assessmentReview().reason()));
    }

    private static Long epoch(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
