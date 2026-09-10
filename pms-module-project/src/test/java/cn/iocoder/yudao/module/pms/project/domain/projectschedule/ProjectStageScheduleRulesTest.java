package cn.iocoder.yudao.module.pms.project.domain.projectschedule;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.pms.project.domain.projectschedule.ProjectStageScheduleRules.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStageScheduleRulesTest {
    private final LocalDate deadline = LocalDate.of(2026, 12, 31);
    private StageAllocation allocation(long id, String code, String percentage) {
        return new StageAllocation(id, code, new BigDecimal(percentage));
    }
    private CalculatedStageDates shortenedPlan() {
        return calculate(deadline, 10, List.of(allocation(20, "S2", "40"), allocation(40, "S4", "60")));
    }

    @Test void backwardPlanningUsesSurveyDeadlineAndOnlyTheRealPath() {
        var plan = shortenedPlan();
        assertEquals(LocalDate.of(2026, 12, 22), plan.startDate());
        assertEquals(deadline, plan.endDate());
        assertEquals(List.of(
                new StageDates(20L, "S2", LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 25)),
                new StageDates(40L, "S4", LocalDate.of(2026, 12, 26), deadline)), plan.stages());
        assertThrows(UnsupportedOperationException.class, () -> plan.stages().clear());
    }

    @Test void cumulativeRoundingKeepsAllCalendarDaysWithoutOverlap() {
        var plan = calculate(LocalDate.of(2028, 3, 2), 7, List.of(
                allocation(10, "S1", "25"), allocation(20, "S2", "25"), allocation(40, "S4", "50")));
        assertEquals(LocalDate.of(2028, 2, 25), plan.startDate());
        assertEquals(LocalDate.of(2028, 2, 25), plan.stages().get(0).endDate());
        assertEquals(LocalDate.of(2028, 2, 26), plan.stages().get(1).startDate());
        assertEquals(LocalDate.of(2028, 2, 27), plan.stages().get(1).endDate());
        assertEquals(LocalDate.of(2028, 2, 28), plan.stages().get(2).startDate());
        assertEquals(LocalDate.of(2028, 3, 2), plan.stages().get(2).endDate());
    }

    @Test void missingOrInvalidAllocationsDoNotFallBackToDefaultStageDurations() {
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 30, List.of()));
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 30, List.of(allocation(20, "S2", "99.99"))));
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 30, List.of(allocation(20, "S2", "0"), allocation(40, "S4", "100"))));
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 30, List.of(allocation(20, "S2", "50"), allocation(20, "S4", "50"))));
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 30, List.of(allocation(20, "S2", "50"), allocation(21, "S2", "50"))));
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 1, List.of(allocation(20, "S2", "50"), allocation(40, "S4", "50"))));
        assertThrows(IllegalArgumentException.class, () -> calculate(null, 30, List.of(allocation(20, "S2", "100"))));
        assertThrows(IllegalArgumentException.class, () -> calculate(deadline, 0, List.of(allocation(20, "S2", "100"))));
    }

    @Test void stageAdjustmentRequiresAReasonAndKeepsOriginalCalculationUntouched() {
        var original = shortenedPlan();
        var adjusted = List.of(
                new StageDates(20L, "S2", original.startDate(), LocalDate.of(2026, 12, 24)),
                original.stages().get(1));
        assertDoesNotThrow(() -> validateStageAdjustment(original, original.stages(), null));
        assertThrows(IllegalArgumentException.class, () -> validateStageAdjustment(original, adjusted, "  "));
        assertDoesNotThrow(() -> validateStageAdjustment(original, adjusted, "现场准备提前完成"));
        assertEquals(LocalDate.of(2026, 12, 25), original.stages().get(0).endDate());
    }

    @Test void stageAdjustmentCannotChangeMembershipOrderOrProduceOverlapAndOverflow() {
        var plan = shortenedPlan();
        var first = plan.stages().get(0);
        var last = plan.stages().get(1);
        assertThrows(IllegalArgumentException.class, () -> validateStageAdjustment(plan, List.of(first), "reason"));
        assertThrows(IllegalArgumentException.class, () -> validateStageAdjustment(plan, List.of(last, first), "reason"));
        assertThrows(IllegalArgumentException.class, () -> validateStageAdjustment(plan, List.of(first,
                new StageDates(40L, "S4", first.endDate(), deadline)), "reason"));
        assertThrows(IllegalArgumentException.class, () -> validateStageAdjustment(plan, List.of(first,
                new StageDates(40L, "S4", last.startDate(), deadline.plusDays(1))), "reason"));
        assertThrows(IllegalArgumentException.class, () -> validateStageAdjustment(plan, List.of(first,
                new StageDates(50L, "S5", last.startDate(), deadline)), "reason"));
    }

    @Test void taskDatesCanRunInParallelButMustBelongToTheirActualStage() {
        var plan = shortenedPlan();
        var scope = List.of(new TaskScope(1L, 40L), new TaskScope(2L, 40L));
        var task = new TaskDates(1L, LocalDate.of(2026, 12, 26), deadline);
        assertDoesNotThrow(() -> validateTaskDates(plan.stages(), scope,
                List.of(task, new TaskDates(2L, task.startDate(), task.endDate()))));
        assertThrows(IllegalArgumentException.class, () -> validateTaskDates(plan.stages(), scope,
                List.of(new TaskDates(1L, plan.startDate(), deadline))));
        assertThrows(IllegalArgumentException.class, () -> validateTaskDates(plan.stages(), scope,
                List.of(new TaskDates(3L, task.startDate(), deadline))));
        assertThrows(IllegalArgumentException.class, () -> validateTaskDates(plan.stages(), scope, List.of(task, task)));
        assertThrows(IllegalArgumentException.class, () -> validateTaskDates(plan.stages(),
                List.of(new TaskScope(1L, 50L)), List.of(task)));
        assertThrows(IllegalArgumentException.class, () -> validateTaskDates(plan.stages(), scope,
                List.of(new TaskDates(1L, null, deadline))));
        assertThrows(IllegalArgumentException.class, () -> validateTaskDates(plan.stages(), scope,
                List.of(new TaskDates(1L, deadline, task.startDate()))));
    }
}
