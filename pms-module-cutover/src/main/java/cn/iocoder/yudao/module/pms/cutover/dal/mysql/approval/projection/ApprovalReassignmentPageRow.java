package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalReassignmentPageRow {
    private Long approvalInstanceId; private Integer approvalVersion; private Long taskId; private Long projectId;
    private String taskCode; private String taskName; private String grade; private String status; private String holdReason;
    private Long nodeId; private Integer nodeNo; private String nodeCode; private String nodeStatus;
    private Long currentApproverUserId; private Integer nodeVersion; private LocalDateTime createdAt;
}
