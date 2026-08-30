package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record ChecklistCommandResult(Long taskId, Long checklistId, Integer checklistVersion,
                                     Integer checklistFactVersion, String checklistStatus,
                                     String taskStage, Integer taskVersion, boolean replayed) {
    public ChecklistCommandResult replayedCopy() {
        return new ChecklistCommandResult(taskId, checklistId, checklistVersion, checklistFactVersion,
                checklistStatus, taskStage, taskVersion, true);
    }
}
