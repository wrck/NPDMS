package cn.iocoder.yudao.module.pms.cutover.service.task.result;

public record CutoverTaskCommandResult(Long taskId, String taskNo, String currentStage,
                                       String taskStatus, Long version, boolean replayed) {
    public CutoverTaskCommandResult replayedCopy() {
        return new CutoverTaskCommandResult(taskId, taskNo, currentStage, taskStatus, version, true);
    }
}
