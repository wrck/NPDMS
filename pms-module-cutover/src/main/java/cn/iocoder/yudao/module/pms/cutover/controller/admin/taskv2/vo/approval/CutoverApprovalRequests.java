package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2.vo.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.command.AssessmentReviewInput;
import cn.iocoder.yudao.module.pms.cutover.service.approval.command.ReviewItemInput;

import java.util.List;

public final class CutoverApprovalRequests {
    private CutoverApprovalRequests() { }

    public record Decision(List<ReviewItemInput> reviewItems, AssessmentReviewInput assessmentReview,
                           String feedback) { }
    public record Reassign(Integer nodeNo, Long newApproverUserId, String reason) { }
}
