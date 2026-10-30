package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_satisfaction_questionnaire_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionQuestionnaireTemplateDO extends BaseBusinessEntity {
    private String templateCode;
    private String name;
    private String status;
    private Long currentRevisionId;
}
