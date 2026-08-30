package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("acc_satisfaction_questionnaire")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionQuestionnaireDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long collectionTaskId;
    private Long templateId;
    private Long templateRevisionId;
    private Integer templateVersion;
    private String frozenQuestionJson;
    private BigDecimal frozenThreshold;
    private String ruleVersion;
    private String questionnaireStatus;
    private Long accessScopeVersion;
    private Integer version;
}
