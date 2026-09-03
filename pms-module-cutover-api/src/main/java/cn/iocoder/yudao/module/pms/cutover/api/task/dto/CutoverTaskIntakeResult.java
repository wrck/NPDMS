package cn.iocoder.yudao.module.pms.cutover.api.task.dto;

/** 可信内部来源创建割接任务的结果。 */
public record CutoverTaskIntakeResult(
        Long taskId,
        String taskNo,
        String currentStage,
        String taskStatus,
        Integer version,
        boolean replayed) {
}
