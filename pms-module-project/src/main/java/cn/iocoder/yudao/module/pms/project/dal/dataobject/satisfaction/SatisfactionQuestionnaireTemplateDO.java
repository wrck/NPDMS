package cn.iocoder.yudao.module.pms.project.dal.dataobject.satisfaction;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_satisfaction_questionnaire_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class SatisfactionQuestionnaireTemplateDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String templateCode;
    private String name;
    private String status;
    private Long currentRevisionId;
    private Integer version;
}
