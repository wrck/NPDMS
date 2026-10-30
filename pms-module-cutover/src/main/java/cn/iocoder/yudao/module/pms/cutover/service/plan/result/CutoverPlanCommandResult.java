package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

public record CutoverPlanCommandResult(Long taskId, Long taskVersion, Long planRevisionId,
                                       Integer revisionNo, Long planVersion, String status,
                                       boolean replayed) {
    public CutoverPlanCommandResult replayedCopy() {
        return new CutoverPlanCommandResult(taskId, taskVersion, planRevisionId, revisionNo,
                planVersion, status, true);
    }
}
