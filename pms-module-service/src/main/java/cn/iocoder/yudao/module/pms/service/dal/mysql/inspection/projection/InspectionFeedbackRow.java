package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InspectionFeedbackRow {
    private Long annotationId, taskId, projectId, resultId, ruleRevisionId, ruleId, annotatedBy, confirmationId, confirmedBy;
    private Integer annotationRevision, revisionNo, confirmationVersion;
    private String detectionId, inspectionItem, categoryCode, categoryName, severityCode, severityName;
    private String taskCode, deviceSerial, productModel, outputSummary, reason, confirmationReason;
    private LocalDateTime annotatedAt, confirmedAt, detectedAt;
    private Boolean activeFlag, validFlag;
    private String commands;
}
