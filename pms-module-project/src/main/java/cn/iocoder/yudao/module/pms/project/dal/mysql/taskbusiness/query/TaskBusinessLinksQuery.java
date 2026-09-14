package cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query;

/** A business-reference lookup for exactly one task or stage. */
public record TaskBusinessLinksQuery(Long tenantId, Long projectId, Long taskId, Long stageId) {
    public TaskBusinessLinksQuery(Long tenantId, Long projectId, Long taskId) {
        this(tenantId, projectId, taskId, null);
    }
    public static TaskBusinessLinksQuery stage(Long tenantId, Long projectId, Long stageId) {
        return new TaskBusinessLinksQuery(tenantId, projectId, null, stageId);
    }
}
