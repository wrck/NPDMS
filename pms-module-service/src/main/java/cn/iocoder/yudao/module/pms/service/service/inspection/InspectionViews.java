package cn.iocoder.yudao.module.pms.service.service.inspection;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

/** Public business views, without tenant, persistence flags, credentials or file storage locations. */
public final class InspectionViews {
    private InspectionViews() { }

    public record AttachmentView(Long artifactId, Integer versionNo, String referenceKey, String name,
                                 Long sizeBytes, String availabilityStatus) { }

    @Data
    public static class TaskSummary {
        private Long id;
        private Long version;
        private String taskCode;
        private String taskName;
        private Long projectId;
        private String projectCode;
        private String projectName;
        private Long customerId;
        private String customerName;
        private Long ownerUserId;
        private String ownerName;
        private String creatorName;
        private String background;
        private LocalDateTime plannedAt;
        private String mode;
        private String statusCode;
        private Long currentAttemptId;
        private Long currentReportId;
        private LocalDateTime archiveRequestedAt;
        private Long archiveRequestedBy;
        private LocalDateTime archivedAt;
        private Long archivedBy;
        private Long customerArchiveId;
        private String cancellationReason;
        private LocalDateTime createTime;
    }

    @Data
    public static class DeviceView {
        private Long id;
        private Long deviceId;
        private String serialNumber;
        private String deviceName;
        private String productTypeCode;
        private String productTypeName;
        private String productModel;
        private String contractNo;
    }

    @Data
    public static class SelectionView {
        private Long deviceId;
        private Long ruleRevisionId;
    }

    @Data
    public static class AttemptView {
        private Long id;
        private Integer attemptNo;
        private String mode;
        private String statusCode;
        private String reason;
        private Long startedBy;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }

    @Data
    public static class ExecutionView {
        private Long id;
        private Long version;
        private Long attemptId;
        private Long deviceId;
        private String purposeCode;
        private String statusCode;
        private String fileName;
        private Long artifactId;
        private Integer fileVersionNo;
        private String fileReferenceKey;
        private String softwareVersion;
        private String parserTaskId;
        private String parserRelease;
        private String recognizedSerial;
        private String failureCode;
        private String failureMessage;
        private Long collectionExecutionId;
        private String collectionTaskId;
        private Integer collectionResultVersion;
        private String collectionStatus;
        private String collectionFailureCategory;
        private Long submittedBy;
        private String protocol;
        private String host;
        private Integer port;
        private String username;
        private Long credentialId;
        private Long precheckExecutionId;
        private LocalDateTime submittedAt;
        private LocalDateTime completedAt;
        private LocalDateTime precheckExpiresAt;
        private LocalDateTime deviceStartedAt;
        private LocalDateTime deviceCompletedAt;
    }

    @Data
    public static class CollectionReceiptView {
        private Long id, executionId, collectionExecutionId, resultVersion, fileVersionId;
        private String collectionTaskId, externalStatus, failureCategory;
        private LocalDateTime receivedAt;
    }

    @Data
    public static class ResultView {
        private Long id;
        private Long attemptId;
        private Long executionId;
        private Long deviceId;
        private Long ruleRevisionId;
        private String ruleName;
        private String categoryCode;
        private String categoryName;
        private String severityCode;
        private String severityName;
        private String outcome;
        private BigDecimal measuredValue;
        private String measuredUnit;
        private Boolean regexMatched;
        private String outputSummary;
        private String detailCode;
        private String detailMessage;
    }

    @Data
    public static class ReportFileView {
        private Long id;
        private String formatCode;
        private String fileName;
        private Long artifactId;
        private Integer fileVersionNo;
        private String referenceKey;
    }

    @Data
    public static class ReportView {
        private Long id;
        private Long attemptId;
        private Integer revisionNo;
        private String sourceCode;
        private String statusCode;
        private String templateVersion;
        private String explanation;
        private String failureMessage;
        private LocalDateTime generatedAt;
        private Long generatedBy;
        private List<ReportFileView> files;
        private List<Long> resultIds;
    }

    @Data
    public static class AnnotationView {
        private Long id;
        private Long reportId;
        private Long resultId;
        private Integer revisionNo;
        private String kind;
        private String reason;
        private String changeReason;
        private Long responsibleUserId;
        private LocalDateTime dueAt;
        private Long annotatedBy;
        private LocalDateTime annotatedAt;
    }

    @Data
    public static class IssueView {
        private Long id;
        private Long version;
        private Long taskId;
        private Long reportId;
        private Long resultId;
        private Long responsibleUserId;
        private String responsibleName;
        private LocalDateTime dueAt;
        private String description;
        private String severityCode;
        private String statusCode;
        private String closureDescription;
        private Long closureRequestedBy;
        private LocalDateTime closureRequestedAt;
        private Long closedBy;
        private LocalDateTime closedAt;
    }

    @Data
    public static class RemediationView {
        private Long id;
        private Long issueId;
        private String actionCode;
        private String content;
        private Long actorId;
        private String actorName;
        private LocalDateTime actionAt;
    }

    @Data
    public static class EvidenceView {
        private Long id;
        private Long remediationId;
        private Long artifactId;
        private Integer fileVersionNo;
        private String referenceKey;
        private String fileName;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class IssueDetail extends IssueView {
        private String taskCode;
        private String taskName;
        private String taskStatus;
        private List<RemediationView> remediations;
        private List<EvidenceView> evidence;
        private boolean responsible;
        private boolean canConfirm;
    }

    @Data @EqualsAndHashCode(callSuper = true)
    public static class IssueSummary extends IssueView {
        private String taskCode;
        private String taskName;
    }
    public record Workbench(List<cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection.InspectionStateCount> taskCounts,
                            List<TaskSummary> pendingTasks, List<TaskSummary> createdTasks, List<IssueSummary> myIssues) { }

    @Data
    public static class EventView {
        private Long id;
        private String actionCode;
        private String fromStatus;
        private String toStatus;
        private Long actorId;
        private String actorName;
        private String description;
        private LocalDateTime occurredAt;
    }
    @Data
    public static class RuleFeedbackView {
        private Long id, deviceId, reportedBy;
        private String reportedName;
        private String description;
        private LocalDateTime reportedAt;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TaskDetail extends TaskSummary {
        private List<DeviceView> devices;
        private List<SelectionView> selections;
        private List<AttemptView> attempts;
        private List<ExecutionView> executions;
        private List<CollectionReceiptView> collectionReceipts;
        private List<ResultView> results;
        private List<ReportView> reports;
        private List<AnnotationView> annotations;
        private List<IssueView> issues;
        private List<RemediationView> remediations;
        private List<EvidenceView> evidence;
        private List<EventView> events;
        private List<RuleFeedbackView> ruleFeedback;
        private boolean manager;
        private boolean owner;
    }
}
