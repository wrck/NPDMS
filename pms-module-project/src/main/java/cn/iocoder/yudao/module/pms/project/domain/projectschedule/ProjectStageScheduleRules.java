package cn.iocoder.yudao.module.pms.project.domain.projectschedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PLN-01: project-owned stage dates and task dates within those stages.
 * The caller supplies the authoritative deadline, duration and ordered frozen
 * path. Results are calculation values, not a separate plan entity. The project
 * application service owns writes to the existing stage/task instances.
 * This class neither invents stages/percentages nor changes lifecycle state.
 */
public final class ProjectStageScheduleRules {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private ProjectStageScheduleRules() { }

    public record StageAllocation(Long stageId, String stageCode, BigDecimal percentage) { }
    public record StageDates(Long stageId, String stageCode, LocalDate startDate, LocalDate endDate) { }
    public record TaskScope(Long taskId, Long stageId) { }
    public record TaskDates(Long taskId, LocalDate startDate, LocalDate endDate) { }
    public record CalculatedStageDates(LocalDate startDate, LocalDate endDate, int durationDays,
                                 List<StageDates> stages) {
        public CalculatedStageDates { stages = List.copyOf(stages); }
    }

    /** Inclusive calendar days; cumulative boundaries avoid gaps and rounding overflow. */
    public static CalculatedStageDates calculate(LocalDate requiredEndDate, int durationDays,
                                           List<StageAllocation> orderedPath) {
        require(requiredEndDate != null && durationDays > 0, "工勘结束日期和正整数工期天数不能为空");
        validateAllocations(orderedPath);
        final LocalDate start;
        try {
            start = requiredEndDate.minusDays(durationDays - 1L);
        } catch (DateTimeException failure) {
            throw new IllegalArgumentException("倒排日期超出允许范围", failure);
        }
        var stages = new java.util.ArrayList<StageDates>();
        BigDecimal cumulativePercentage = BigDecimal.ZERO;
        int consumedDays = 0;
        for (StageAllocation stage : orderedPath) {
            cumulativePercentage = cumulativePercentage.add(stage.percentage());
            int boundary = BigDecimal.valueOf(durationDays).multiply(cumulativePercentage)
                    .divide(ONE_HUNDRED, 0, RoundingMode.FLOOR).intValueExact();
            require(boundary > consumedDays, "阶段 " + stage.stageCode() + " 的占比不足以分配一个自然日");
            stages.add(new StageDates(stage.stageId(), stage.stageCode(),
                    start.plusDays(consumedDays), start.plusDays(boundary - 1L)));
            consumedDays = boundary;
        }
        return new CalculatedStageDates(start, requiredEndDate, durationDays, stages);
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

    private static void validateAllocations(List<StageAllocation> stages) {
        require(stages != null && !stages.isEmpty(), "实际计划路径不能为空");
        Set<Long> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (StageAllocation stage : stages) {
            require(stage != null && positive(stage.stageId()) && stage.stageCode() != null
                            && stage.stageCode().matches("S[0-6]"), "阶段身份无效");
            require(ids.add(stage.stageId()) && codes.add(stage.stageCode()), "实际计划路径包含重复阶段");
            require(stage.percentage() != null && stage.percentage().signum() > 0
                            && stage.percentage().compareTo(ONE_HUNDRED) <= 0,
                    "阶段 " + stage.stageCode() + " 的工期占比必须大于零且不超过100%");
            total = total.add(stage.percentage());
        }
        require(total.compareTo(ONE_HUNDRED) == 0, "参与阶段工期占比之和必须为100%");
    }

    private static boolean positive(Long value) { return value != null && value > 0; }

    private static void validateDates(LocalDate start, LocalDate end, String label) {
        require(start != null && end != null && !end.isBefore(start), label + " 的起止日期缺失或逆序");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
