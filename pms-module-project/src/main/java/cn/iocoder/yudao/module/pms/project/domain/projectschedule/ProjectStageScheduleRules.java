package cn.iocoder.yudao.module.pms.project.domain.projectschedule;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PLN-01: project-owned stage baselines and task dates within those stages.
 * Stage plan start/end times are direct inputs (Demo 3.1); no schedule
 * percentage participates in this domain. This class neither invents
 * stages nor changes lifecycle state.
 */
public final class ProjectStageScheduleRules {

    private ProjectStageScheduleRules() { }

    public record StageDates(Long stageId, String stageCode, LocalDate startDate, LocalDate endDate) { }
    public record TaskScope(Long taskId, Long stageId) { }
    public record TaskDates(Long taskId, LocalDate startDate, LocalDate endDate) { }
    public record CalculatedStageDates(LocalDate startDate, LocalDate endDate, int durationDays,
                                 List<StageDates> stages) {
        public CalculatedStageDates { stages = List.copyOf(stages); }
    }

    /** Validate a complete stage baseline without changing its frozen membership/order. */
    public static void validateStageAdjustment(CalculatedStageDates calculated, List<StageDates> adjusted,
                                               String adjustmentReason) {
        require(calculated != null && adjusted != null && adjusted.size() == calculated.stages().size(),
                "调整计划必须保留冻结路径中的全部阶段");
        LocalDate previousEnd = null;
        boolean changed = false;
        for (int index = 0; index < adjusted.size(); index++) {
            StageDates original = calculated.stages().get(index);
            StageDates stage = adjusted.get(index);
            require(stage != null && original.stageId().equals(stage.stageId())
                            && original.stageCode().equals(stage.stageCode()),
                    "调整计划不得新增、删除或重排冻结阶段");
            validateDates(stage.startDate(), stage.endDate(), "阶段 " + stage.stageCode());
            require(!stage.startDate().isBefore(calculated.startDate())
                            && !stage.endDate().isAfter(calculated.endDate()),
                    "阶段 " + stage.stageCode() + " 的日期超出项目工期区间");
            require(previousEnd == null || stage.startDate().isAfter(previousEnd),
                    "阶段 " + stage.stageCode() + " 与前序阶段日期重叠或逆序");
            changed |= !original.startDate().equals(stage.startDate()) || !original.endDate().equals(stage.endDate());
            previousEnd = stage.endDate();
        }
        require(!changed || adjustmentReason != null && !adjustmentReason.isBlank(), "调整阶段日期必须填写调整原因");
    }

    /**
     * Validate supplied task dates against authoritative task-to-stage membership.
     * No task ordering is invented: tasks within one stage may run in parallel.
     * Completeness/required planning items remain the caller's frozen-template rule.
     */
    public static void validateTaskDates(List<StageDates> stageBaseline, List<TaskScope> taskScope,
                                         List<TaskDates> taskDates) {
        require(stageBaseline != null && !stageBaseline.isEmpty() && taskScope != null && taskDates != null,
                "阶段基线、任务范围和任务日期不能为空");
        Map<Long, StageDates> stages = new HashMap<>();
        for (StageDates stage : stageBaseline) {
            require(stage != null && positive(stage.stageId()), "阶段身份无效");
            validateDates(stage.startDate(), stage.endDate(), "阶段 " + stage.stageCode());
            require(stages.putIfAbsent(stage.stageId(), stage) == null, "阶段基线包含重复阶段");
        }
        Map<Long, Long> taskStages = new HashMap<>();
        for (TaskScope task : taskScope) {
            require(task != null && positive(task.taskId()) && stages.containsKey(task.stageId()),
                    "任务必须属于当前计划的实际阶段");
            require(taskStages.putIfAbsent(task.taskId(), task.stageId()) == null, "任务范围包含重复任务");
        }
        Set<Long> datedTasks = new HashSet<>();
        for (TaskDates task : taskDates) {
            require(task != null && taskStages.containsKey(task.taskId()), "任务日期引用了范围外任务");
            require(datedTasks.add(task.taskId()), "同一任务不能重复填写日期");
            validateDates(task.startDate(), task.endDate(), "任务 " + task.taskId());
            StageDates stage = stages.get(taskStages.get(task.taskId()));
            require(!task.startDate().isBefore(stage.startDate()) && !task.endDate().isAfter(stage.endDate()),
                    "任务 " + task.taskId() + " 的日期超出所属阶段 " + stage.stageCode());
        }
    }

    private static boolean positive(Long value) { return value != null && value > 0; }

    private static void validateDates(LocalDate start, LocalDate end, String label) {
        require(start != null && end != null && !end.isBefore(start), label + " 的起止日期缺失或逆序");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
