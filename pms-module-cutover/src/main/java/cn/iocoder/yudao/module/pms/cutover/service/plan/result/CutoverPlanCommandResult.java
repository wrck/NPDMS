package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

public record CutoverPlanCommandResult(Long taskId, Integer taskVersion, Long planRevisionId,
                                       Integer revisionNo, Integer planVersion, String status,
                                       boolean replayed) {
    public CutoverPlanCommandResult replayedCopy() {
        return new CutoverPlanCommandResult(taskId, taskVersion, planRevisionId, revisionNo,
                planVersion, status, true);
    }
}
