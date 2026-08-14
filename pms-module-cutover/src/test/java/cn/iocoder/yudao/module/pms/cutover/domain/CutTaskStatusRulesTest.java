package cn.iocoder.yudao.module.pms.cutover.domain;

import cn.iocoder.yudao.module.pms.cutover.enums.CutStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutTaskStatusRulesTest {

    @Test
    void shouldKeepOnlyCurrentReviewTransitions() {
        assertEquals(CutStatusEnum.CUT_TASK_PENDING_REVIEW,
                CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.SUBMIT_FOR_REVIEW));
        assertEquals(CutStatusEnum.CUT_TASK_CLOSURE_IN_PROGRESS,
                CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.APPROVE));
        assertEquals(CutStatusEnum.CUT_TASK_PREPARING,
                CutTaskStatusRules.targetStatus(CutTaskStatusRules.Action.REJECT));
        assertDoesNotThrow(() -> CutTaskStatusRules.requireTransition(
                CutStatusEnum.CUT_TASK_PREPARING, CutTaskStatusRules.Action.SUBMIT_FOR_REVIEW));
        assertThrows(IllegalStateException.class, () -> CutTaskStatusRules.requireTransition(
                CutStatusEnum.CUT_TASK_CLOSURE_IN_PROGRESS, CutTaskStatusRules.Action.SUBMIT_FOR_REVIEW));
    }

    @Test
    void shouldTreatLegacyTerminalStatusesAsReadOnlyWithoutRejectingNull() {
        assertFalse(CutTaskStatusRules.isTerminal(null));
        assertFalse(CutTaskStatusRules.isTerminal(CutStatusEnum.CUT_TASK_CLOSURE_IN_PROGRESS));
        assertTrue(CutTaskStatusRules.isTerminal(6));
        assertTrue(CutTaskStatusRules.isTerminal(7));
        assertTrue(CutTaskStatusRules.isTerminal(8));
    }
}
