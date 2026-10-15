package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data @EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_feedback_export")
public class InspectionFeedbackExportDO extends BaseBusinessEntity {
    private Long exportedBy;
    private String formatCode, statusCode;
    private Long ruleRevisionId;
    private String detectionId, categoryCode, severityCode, productModel, confirmation;
    private LocalDateTime fromAt, toAt, dataAsOf;
    private Boolean currentOnly, includeDetails;
    private Integer resultCount;
    private Long artifactId;
    private Integer fileVersionNo;
    private String referenceKey, fileName, sha256, failureMessage;
    private LocalDateTime generatedAt;
}
