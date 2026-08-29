package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import java.time.LocalDateTime;

public final class AcceptanceReportCommands {

    private AcceptanceReportCommands() {
    }

    public record Actor(Long tenantId, Long userId, String correlationId) {
    }

    public record DraftContent(LocalDateTime acceptanceTime, String conclusionCode,
                               String conclusionText, String acceptorName) {
    }

    public record CreateDraftCommand(Long acceptanceId, Integer expectedActivityVersion,
                                     DraftContent content) {
    }

    public record UpdateDraftCommand(Long acceptanceId, Long reportVersionId,
                                     Integer expectedActivityVersion, Integer expectedReportVersionNo,
                                     DraftContent content) {
    }

    public record PublishCommand(Long acceptanceId, Long reportVersionId,
                                 Integer expectedActivityVersion, Integer expectedReportVersionNo,
                                 Long expectedCurrentReportVersionId, String idempotencyKey,
                                 String requestDigest) {
    }

    public record RevokeCommand(Long acceptanceId, Integer expectedActivityVersion,
                                Long expectedCurrentReportVersionId, Integer expectedCurrentReportVersionNo,
                                String idempotencyKey, String requestDigest) {
    }

    public record ReportResult(Long acceptanceId, Long reportVersionId, Integer reportVersionNo,
                               String reportStatus, String changeType, boolean replayed) {
    }
}
