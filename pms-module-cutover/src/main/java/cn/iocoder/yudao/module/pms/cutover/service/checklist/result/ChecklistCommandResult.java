package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

public record ChecklistCommandResult(Long taskId, Long checklistId, Long checklistVersion,
                                     Long checklistFactVersion, String checklistStatus,
                                     String taskStage, Long taskVersion, boolean replayed,
                                     NavigationDecision navigationDecision) {
    public ChecklistCommandResult(Long taskId, Long checklistId, Long checklistVersion,
                                  Long checklistFactVersion, String checklistStatus,
                                  String taskStage, Long taskVersion, boolean replayed) {
        this(taskId, checklistId, checklistVersion, checklistFactVersion, checklistStatus,
                taskStage, taskVersion, replayed, null);
    }

    public ChecklistCommandResult replayedCopy() {
        return new ChecklistCommandResult(taskId, checklistId, checklistVersion, checklistFactVersion,
                checklistStatus, taskStage, taskVersion, true, navigationDecision);
    }

    public ChecklistCommandResult withNavigationDecision(NavigationDecision decision) {
        return new ChecklistCommandResult(taskId, checklistId, checklistVersion, checklistFactVersion,
                checklistStatus, taskStage, taskVersion, replayed, decision);
    }
}
