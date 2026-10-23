package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_report_revision")
public class InspectionReportRevisionDO extends BaseBusinessEntity {
    private Long taskId;
    private Long attemptId;
    private Integer revisionNo;
    private String sourceCode;
    private String statusCode;
    private String templateVersion;
    private String inputDigest;
    private String explanation;
    private String failureMessage;
    private Long generatedBy;
    private java.time.LocalDateTime generatedAt;
}
