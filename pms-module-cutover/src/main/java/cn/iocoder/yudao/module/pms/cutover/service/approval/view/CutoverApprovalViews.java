package cn.iocoder.yudao.module.pms.cutover.service.approval.view;

import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCompliance;
import cn.iocoder.yudao.module.pms.cutover.service.spare.view.CutoverSpareViews;

import java.time.LocalDateTime;
import java.util.List;

public final class CutoverApprovalViews {
    private CutoverApprovalViews() { }

    public sealed interface ApprovalView permits ApprovalDetail, ApprovalFinalResult, ApprovalReassignmentView { }
    public record ReviewItem(String itemCode, String decision, String unreasonableReason) { }
    public record AssessmentReview(String decision, String reason) { }
    public record Node(Long nodeId, Integer nodeNo, String nodeCode, String status,
                       Long originalApproverUserId, Long currentApproverUserId, LocalDateTime decisionAt,
                       String feedback, List<ReviewItem> reviewItems, AssessmentReview assessmentReview) { }
    public record ApprovalDetail(String viewMode, Long approvalInstanceId, Integer approvalVersion,
                                 Long taskId, Integer taskVersion, Long planRevisionId, Integer planRevisionNo,
                                 String grade, String status, String holdReason, Integer currentNodeNo,
                                 List<Node> nodes, CutoverApprovalSourceSnapshotCodec.ApprovalSourceSnapshot sourceSnapshot,
                                 CutoverLeadTimeCompliance leadTimeCompliance, LocalDateTime decisionAt, String rejectionReason,
                                 CutoverSpareViews.ApprovalSummary spareSupport,
                                 List<String> allowedActions) implements ApprovalView { }
    public record ApprovalFinalResult(String viewMode, Long approvalInstanceId, Long taskId,
                                      Long planRevisionId, String grade, String status,
                                      LocalDateTime decisionAt, String rejectionReason,
                                      List<String> allowedActions) implements ApprovalView { }
    public record ReassignmentNode(Long nodeId, Integer nodeNo, String nodeCode, String nodeStatus,
                                   Long currentApproverUserId, Integer nodeVersion) { }
    public record ApprovalReassignmentView(String viewMode, Long approvalInstanceId, Integer approvalVersion,
                                           Long taskId, Long projectId, String taskCode, String taskName,
                                           String grade, String status, String holdReason,
                                           List<ReassignmentNode> nodes,
                                           List<String> allowedActions) implements ApprovalView { }
    public record TodoItem(Long approvalInstanceId, Integer approvalVersion, Long taskId, Long projectId,
                           String taskCode, String taskName, String grade, Integer nodeNo,
                           String nodeCode, LocalDateTime createdAt) { }
    public record ReassignmentCandidate(Long approvalInstanceId, Integer approvalVersion, Long taskId,
                                        Long projectId, String taskCode, String taskName, String grade,
                                        String status, String holdReason, Long nodeId, Integer nodeNo,
                                        String nodeCode, String nodeStatus, Long currentApproverUserId,
                                        Integer nodeVersion, LocalDateTime createdAt) { }
    public record Page<T>(List<T> list, long total, int pageNo, int pageSize) { }
}
