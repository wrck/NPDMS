package cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApprovalTodoPageRow {
    private Long nodeId; private Long approvalInstanceId; private Integer approvalVersion; private Long taskId; private Long projectId;
    private String taskCode; private String taskName; private String grade; private Integer nodeNo;
    private String nodeCode; private LocalDateTime createdAt;
}
