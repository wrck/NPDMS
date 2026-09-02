package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("acc_satisfaction_questionnaire_template_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionQuestionnaireTemplateRevisionDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long templateId;
    private Integer revisionNo;
    private String projectType;
    private String signingMode;
    private String implementationMode;
    private String businessPurposeCode;
    private String applicableTimingCode;
    private Integer priority;
    private String frozenQuestionJson;
    private BigDecimal frozenThreshold;
    private String ruleVersion;
    private String revisionStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer version;
}
