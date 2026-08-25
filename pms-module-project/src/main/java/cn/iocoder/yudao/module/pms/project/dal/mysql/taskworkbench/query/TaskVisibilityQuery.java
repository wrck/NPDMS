package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 当前主体的任务正文与祖先占位范围。 */
public record TaskVisibilityQuery(
        Long tenantId,
        Long projectId,
        Long actorId,
        boolean fullProjectAccess) {
}
