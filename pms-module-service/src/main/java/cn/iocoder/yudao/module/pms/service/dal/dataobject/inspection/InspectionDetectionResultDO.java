package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_detection_result")
public class InspectionDetectionResultDO extends BaseBusinessEntity {
    private Long taskId;
    private Long attemptId;
    private Long executionId;
    private Long ruleSnapshotId;
    private Long deviceId;
    private Long ruleRevisionId;
    private String outcome;
    private java.math.BigDecimal measuredValue;
    private String measuredUnit;
    private Boolean regexMatched;
    private String outputSummary;
    private String detailCode;
    private String detailMessage;
}
