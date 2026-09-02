package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record CutoverApprovalStartCommand(
        Long tenantId,
        Long taskId,
        Integer expectedTaskVersion,
        Long planRevisionId,
        Integer planRevisionNo,
        String grade,
        Long assessmentId,
        Integer assessmentVersion,
        Long checklistId,
        Integer checklistVersion,
        Integer sourceSnapshotVersion,
        LocalDateTime planSubmittedAt,
        Long previousApprovalInstanceId,
        String idempotencyKey,
        String correlationId) {

    private static final Set<String> GRADES = Set.of("A", "B", "C", "D");

    public CutoverApprovalStartCommand {
        ApprovalContractRules.positive(tenantId, "tenantId");
        ApprovalContractRules.positive(taskId, "taskId");
        ApprovalContractRules.nonNegative(expectedTaskVersion, "expectedTaskVersion");
        ApprovalContractRules.positive(planRevisionId, "planRevisionId");
        ApprovalContractRules.positive(planRevisionNo, "planRevisionNo");
        if (!GRADES.contains(grade)) {
            throw ApprovalContractRules.invalid("grade is invalid");
        }
        ApprovalContractRules.positive(assessmentId, "assessmentId");
        ApprovalContractRules.positive(assessmentVersion, "assessmentVersion");
        if ("D".equals(grade)) {
            if (checklistId != null || checklistVersion != null) {
                throw ApprovalContractRules.invalid("D grade must not carry checklist identity");
            }
        } else {
            ApprovalContractRules.positive(checklistId, "checklistId");
            ApprovalContractRules.positive(checklistVersion, "checklistVersion");
        }
        ApprovalContractRules.positive(sourceSnapshotVersion, "sourceSnapshotVersion");
        if (planSubmittedAt == null) {
            throw ApprovalContractRules.invalid("planSubmittedAt is required");
        }
        ApprovalContractRules.nullablePositive(previousApprovalInstanceId, "previousApprovalInstanceId");
        ApprovalContractRules.normalized(idempotencyKey, 128, "idempotencyKey");
        ApprovalContractRules.normalized(correlationId, 128, "correlationId");
    }
}
