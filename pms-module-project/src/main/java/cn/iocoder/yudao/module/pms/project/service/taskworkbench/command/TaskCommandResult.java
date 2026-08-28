package cn.iocoder.yudao.module.pms.project.service.taskworkbench.command;

public record TaskCommandResult(Long taskId, int taskVersion, long taskTreeVersion,
                                String status, String replayDecision) {
}
