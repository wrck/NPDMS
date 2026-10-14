package cn.iocoder.yudao.module.pms.cutover.api.result;

/** 只唤醒消费者；真实成功、范围和文件身份必须通过 CutoverResultApi 重验。 */
public record CutoverCompleted(String eventId, Long tenantId, Long projectId, Long taskId,
                               Long closureId, Long closureRevision, String finalResult, String resultRef,
                               Long archivedAt, String correlationId) {
    public static final String EVENT_TYPE = "CutoverCompleted";
}
