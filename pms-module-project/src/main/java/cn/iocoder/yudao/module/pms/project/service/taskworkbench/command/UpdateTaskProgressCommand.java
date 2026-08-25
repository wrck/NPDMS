package cn.iocoder.yudao.module.pms.project.service.taskworkbench.command;

public record UpdateTaskProgressCommand(Long taskId, Integer expectedTaskVersion, Integer progress) {
}
