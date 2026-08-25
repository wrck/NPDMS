package cn.iocoder.yudao.module.pms.project.domain.projectgovernance;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectStageSnapshotRulesTest {

    @Test
    void sharedSnapshotWithoutGovernanceActionRemainsCompatible() {
        assertDoesNotThrow(() -> ProjectStageSnapshotRules.validateGovernanceAction(
                new ProjectStageSnapshotDO()));
    }

    @Test
    void rollbackRequiresReassignmentAndGuardFacts() {
        ProjectStageSnapshotDO snapshot = common(ProjectStageSnapshotRules.ROLLBACK);
        assertThrows(IllegalArgumentException.class,
                () -> ProjectStageSnapshotRules.validateGovernanceAction(snapshot));

        snapshot.setReassignmentRequirement("重新指派一级服务经理");
        snapshot.setGuardSnapshotJson("{}");
        snapshot.setTreeVersion(3L);
        snapshot.setProviderFactsJson("{}");
        assertDoesNotThrow(() -> ProjectStageSnapshotRules.validateGovernanceAction(snapshot));
    }

    @Test
    void exceptionCloseRequiresBusinessBasisLegacyItemsAndGuardFacts() {
        ProjectStageSnapshotDO snapshot = common(ProjectStageSnapshotRules.EXCEPTION_CLOSE);
        snapshot.setGuardSnapshotJson("{}");
        snapshot.setTreeVersion(3L);
        snapshot.setProviderFactsJson("{}");
        assertThrows(IllegalArgumentException.class,
                () -> ProjectStageSnapshotRules.validateGovernanceAction(snapshot));

        snapshot.setBusinessBasis("客户书面确认终止");
        snapshot.setLegacyItemsJson("[]");
        assertDoesNotThrow(() -> ProjectStageSnapshotRules.validateGovernanceAction(snapshot));
    }

    @Test
    void reopenRequiresConsumedExceptionCloseSnapshot() {
        ProjectStageSnapshotDO snapshot = common(ProjectStageSnapshotRules.REOPEN);
        assertThrows(IllegalArgumentException.class,
                () -> ProjectStageSnapshotRules.validateGovernanceAction(snapshot));

        snapshot.setRelatedSnapshotId(99L);
        assertDoesNotThrow(() -> ProjectStageSnapshotRules.validateGovernanceAction(snapshot));
    }

    private static ProjectStageSnapshotDO common(String operationType) {
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setOperationType(operationType);
        snapshot.setBeforeStage("S3");
        snapshot.setAfterStage("S0");
        snapshot.setBeforeLifecycleStatus("ACTIVE");
        snapshot.setAfterLifecycleStatus("ACTIVE");
        snapshot.setBeforeAssignmentStatus("ASSIGNED");
        snapshot.setAfterAssignmentStatus("UNASSIGNED");
        snapshot.setReasonCode("CONFIGURED_REASON");
        snapshot.setReasonDetail("经审批确认");
        snapshot.setOperationId("operation-1");
        snapshot.setOperatorUserId(1L);
        snapshot.setOperatedAt(LocalDateTime.of(2026, 8, 25, 10, 0));
        return snapshot;
    }
}
