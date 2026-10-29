package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
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
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureMessage;
    private Long generatedBy;
    private java.time.LocalDateTime generatedAt;
    /** Content is fetched explicitly; report lists must not load the large result snapshot. */
    @TableField(select = false)
    private String sourceContextJson;
    @TableField(select = false)
    private String resultJson;
    @TableField(select = false)
    private String resultSha256;
}
