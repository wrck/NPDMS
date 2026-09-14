package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateOutcome;

/** Display/start policy over the Owner's current-round fact, never a second rule or persisted state. */
public record ProjectStageGateProcessState(String processInstanceId, String status,
        ProjectStageGateOutcome outcome, String reasonCode) {
    public static ProjectStageGateProcessState from(ProjectStageGateFact fact) {
        String status = "UNKNOWN";
        if (fact.outcome() == ProjectStageGateOutcome.SATISFIED) status = "APPROVED";
        else if (fact.outcome() == ProjectStageGateOutcome.UNSATISFIED && fact.unmetCode() != null) {
            status = switch (fact.unmetCode()) {
                case "APPROVAL_NOT_STARTED", "PROCESS_NOT_STARTED" -> "NOT_STARTED";
                case "APPROVAL_RUNNING", "PROCESS_RUNNING", "APPROVAL_NOT_COMPLETED", "PROCESS_NOT_COMPLETED" -> "RUNNING";
                case "APPROVAL_REJECTED", "PROCESS_REJECTED" -> "REJECTED";
                case "APPROVAL_CANCELLED", "PROCESS_CANCELLED" -> "CANCELLED";
                default -> "UNKNOWN";
            };
        }
        return new ProjectStageGateProcessState("UNKNOWN".equals(status) || "NOT_STARTED".equals(status)
                ? null : fact.ownerObjectKey(), status, fact.outcome(), fact.unmetCode());
    }

    /** Only state eligibility; project context and original operation permission are enforced by the command. */
    public boolean canStart() {
        return outcome == ProjectStageGateOutcome.UNSATISFIED
                && ("NOT_STARTED".equals(status) || "REJECTED".equals(status) || "CANCELLED".equals(status));
    }
}
