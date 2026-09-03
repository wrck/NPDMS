package cn.iocoder.yudao.module.pms.cutover.service.spare.view;

import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.spare.port.CutoverSpareFilePort.FileFact;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class CutoverSpareViews {
    private CutoverSpareViews() { }

    public record Detail(Long taskId, Integer taskVersion, SpareNeedSnapshot need,
                         List<Application> applications, List<ManualEvidence> manualEvidence,
                         List<String> allowedActions) { }

    public record Application(Long applicationReferenceId, String requestId, String integrationStatus,
                              String externalSystemCode, String externalRequestId,
                              String externalApplicationNo, String launchUrl, Status currentStatus,
                              String lastFailureCode, Integer retryCount, LocalDateTime updatedAt) { }

    public record Status(Long statusVersion, String externalStatusRaw, Map<String, Object> snapshot,
                         String sourceType, LocalDateTime externalOccurredAt, LocalDateTime observedAt) { }

    public record ManualEvidence(Long evidenceId, Long applicationReferenceId, FileFact fileFact,
                                 String description, Long uploadedBy, LocalDateTime createdAt) { }

    public record ApprovalSummary(Boolean required, List<ApplicationApprovalSummary> applications,
                                  List<EvidenceApprovalSummary> manualEvidence) { }

    public record ApplicationApprovalSummary(String integrationStatus, String externalSystemCode,
                                             String externalApplicationNo, String externalStatusRaw,
                                             LocalDateTime observedAt, String lastFailureCode) { }

    public record EvidenceApprovalSummary(String displayName, String description, LocalDateTime uploadedAt) { }
}
