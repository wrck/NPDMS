package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_assessment")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverAssessmentDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long cutoverTaskId;
    private Integer assessmentVersion;
    private String assessmentStatus;
    private String questionnaireTemplateCode;
    private Long questionnaireTemplateVersion;
    private String answerSnapshot;
    private String contextSnapshot;
    private String manualGrade;
    private Boolean simpleFlow;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long invalidatedBy;
    private LocalDateTime invalidatedAt;
    private String invalidationReason;
    private Integer currentMarker;
    @Version
    private Integer version;
}
