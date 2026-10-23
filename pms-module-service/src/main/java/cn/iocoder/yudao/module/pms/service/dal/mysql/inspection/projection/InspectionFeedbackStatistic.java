package cn.iocoder.yudao.module.pms.service.dal.mysql.inspection.projection;

import lombok.Data;

@Data
public class InspectionFeedbackStatistic {
    private Long ruleRevisionId, ruleId;
    private Integer revisionNo;
    private String detectionId, inspectionItem;
    private Long detectionCount, confirmedFalsePositiveCount;
    private java.math.BigDecimal falsePositiveRate;
}
