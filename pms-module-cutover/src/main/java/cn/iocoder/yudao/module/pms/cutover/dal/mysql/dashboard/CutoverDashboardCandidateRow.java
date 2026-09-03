package cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard;

import lombok.Data;

/** CUT-owned current projection used only by the dashboard aggregation. */
@Data
public class CutoverDashboardCandidateRow {
    private Long taskId;
    private Long projectId;
    private String taskOrigin;
    private String currentStage;
    private String taskStatus;
    private Long ownerUserId;
    private String manualGrade;
    private Integer taskVersion;
    private Long stageFactId;
    private Integer stageFactVersion;
    private String currentApprovalStatus;
}
