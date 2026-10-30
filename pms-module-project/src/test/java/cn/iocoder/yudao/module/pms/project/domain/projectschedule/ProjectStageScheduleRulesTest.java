package cn.iocoder.yudao.module.pms.project.domain.projectschedule;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.module.pms.project.domain.projectschedule.ProjectStageScheduleRules.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectStageScheduleRulesTest {
    private final LocalDate deadline = LocalDate.of(2026, 12, 31);
    /** Demo 3.1：阶段基线是调用方给定的直接输入，不再由占比分配产生，这里手工构造两阶段计划。 */
    private CalculatedStageDates shortenedPlan() {
        return new CalculatedStageDates(LocalDate.of(2026, 12, 22), deadline, 10, List.of(
                new StageDates(20L, "S2", LocalDate.of(2026, 12, 22), LocalDate.of(2026, 12, 25)),
                new StageDates(40L, "S4", LocalDate.of(2026, 12, 26), deadline)));
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
        assertThrows(UnsupportedOperationException.class, () -> original.stages().clear());
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
