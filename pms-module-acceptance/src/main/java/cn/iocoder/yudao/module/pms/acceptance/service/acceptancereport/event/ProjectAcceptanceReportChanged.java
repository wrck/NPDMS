package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport.event;

/** Independent project report fact; delivery associations are maintained separately. */
public record ProjectAcceptanceReportChanged(String eventId, Long tenantId, Long projectId, Long acceptanceId,
        Long reportVersionId, String changeType, Long actorId) {
    public static final String EVENT_TYPE = "ACC.ProjectAcceptanceReportChanged.v1";
}
