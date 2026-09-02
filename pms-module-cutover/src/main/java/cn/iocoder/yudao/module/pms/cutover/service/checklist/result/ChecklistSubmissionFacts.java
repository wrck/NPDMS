package cn.iocoder.yudao.module.pms.cutover.service.checklist.result;

/** 平台幂等记录持久化的P3提交事实；导航决策在事务提交后另行附加。 */
public record ChecklistSubmissionFacts(Long taskId, Long checklistId, Integer checklistVersion,
                                       Integer checklistFactVersion, String checklistStatus,
                                       String taskStage, Integer taskVersion, boolean replayed) {

    public ChecklistCommandResult toCommandResult(boolean replayedResult) {
        return new ChecklistCommandResult(taskId, checklistId, checklistVersion, checklistFactVersion,
                checklistStatus, taskStage, taskVersion, replayedResult);
    }
}
