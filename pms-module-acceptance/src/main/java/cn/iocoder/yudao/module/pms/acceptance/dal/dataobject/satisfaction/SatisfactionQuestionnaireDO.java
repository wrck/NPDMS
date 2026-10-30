package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@TableName("acc_satisfaction_questionnaire")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionQuestionnaireDO extends BaseBusinessEntity {
    private Long collectionTaskId;
    private Long templateId;
    private Long templateRevisionId;
    private Integer templateVersion;
    private String frozenQuestionJson;
    private BigDecimal frozenThreshold;
    private String ruleVersion;
    private String questionnaireStatus;
    private Long accessScopeVersion;
}
