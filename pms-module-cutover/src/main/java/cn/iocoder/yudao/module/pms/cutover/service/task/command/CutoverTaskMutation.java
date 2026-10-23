package cn.iocoder.yudao.module.pms.cutover.service.task.command;

/** 现有任务写命令共有的受信身份与并发版本，供各阶段直接复用命令对象。 */
public interface CutoverTaskMutation {
    Long tenantId();
    Long actorId();
    Long taskId();
    Long expectedTaskVersion();
}
