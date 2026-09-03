package cn.iocoder.yudao.module.pms.cutover.service.closure.result;

public record CutoverClosureCommandResult(Long taskId, Integer taskVersion, Long closureId,
                                          Integer closureVersion, String closureStatus, boolean replayed) {
    public CutoverClosureCommandResult replayedCopy() {
        return new CutoverClosureCommandResult(taskId, taskVersion, closureId, closureVersion,
                closureStatus, true);
    }
}
