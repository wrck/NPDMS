package cn.iocoder.yudao.module.pms.project.domain.projectgovernance;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;

/** PM-10动作快照应用层必填规则；共享表的非PM-10行不受这些规则约束。 */
public final class ProjectStageSnapshotRules {

    public static final String ROLLBACK = "ROLLBACK";
    public static final String EXCEPTION_CLOSE = "EXCEPTION_CLOSE";
    public static final String REOPEN = "REOPEN";
    public static final String STAGE_ADVANCE = "STAGE_ADVANCE";

    private ProjectStageSnapshotRules() {
    }

    public static void validateGovernanceAction(ProjectStageSnapshotDO snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        if (snapshot.getOperationType() == null) {
            return;
        }
        requireCommon(snapshot);
        switch (snapshot.getOperationType()) {
            case ROLLBACK -> {
                requireText(snapshot.getReassignmentRequirement(), "reassignmentRequirement");
                requireGuard(snapshot);
            }
            case EXCEPTION_CLOSE -> {
                requireText(snapshot.getBusinessBasis(), "businessBasis");
                requireText(snapshot.getLegacyItemsJson(), "legacyItemsJson");
                requireGuard(snapshot);
            }
            case REOPEN -> requireValue(snapshot.getRelatedSnapshotId(), "relatedSnapshotId");
            case STAGE_ADVANCE -> requireGuard(snapshot);
            default -> throw new IllegalArgumentException("unsupported operationType: " + snapshot.getOperationType());
        }
    }

    private static void requireCommon(ProjectStageSnapshotDO snapshot) {
        requireText(snapshot.getBeforeStage(), "beforeStage");
        requireText(snapshot.getAfterStage(), "afterStage");
        requireText(snapshot.getBeforeLifecycleStatus(), "beforeLifecycleStatus");
        requireText(snapshot.getAfterLifecycleStatus(), "afterLifecycleStatus");
        requireText(snapshot.getBeforeAssignmentStatus(), "beforeAssignmentStatus");
        requireText(snapshot.getAfterAssignmentStatus(), "afterAssignmentStatus");
        requireText(snapshot.getReasonCode(), "reasonCode");
        requireText(snapshot.getReasonDetail(), "reasonDetail");
        requireText(snapshot.getOperationId(), "operationId");
        requireValue(snapshot.getOperatorUserId(), "operatorUserId");
        requireValue(snapshot.getOperatedAt(), "operatedAt");
    }

    private static void requireGuard(ProjectStageSnapshotDO snapshot) {
        requireText(snapshot.getGuardSnapshotJson(), "guardSnapshotJson");
        requireValue(snapshot.getTreeVersion(), "treeVersion");
        requireText(snapshot.getProviderFactsJson(), "providerFactsJson");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireValue(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
    }
}
