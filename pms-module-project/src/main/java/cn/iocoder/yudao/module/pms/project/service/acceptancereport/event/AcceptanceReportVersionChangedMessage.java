package cn.iocoder.yudao.module.pms.project.service.acceptancereport.event;

import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;

import java.util.List;

public record AcceptanceReportVersionChangedMessage(
        String eventId,
        Long tenantId,
        String changeType,
        Long acceptanceId,
        Long projectId,
        String reportType,
        Long publisherActorUserId,
        Long currentReportVersionId,
        Long previousReportVersionId,
        Integer reportVersionNo,
        List<FileArtifactVersionFact> attachments) {

    public AcceptanceReportVersionChangedMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
