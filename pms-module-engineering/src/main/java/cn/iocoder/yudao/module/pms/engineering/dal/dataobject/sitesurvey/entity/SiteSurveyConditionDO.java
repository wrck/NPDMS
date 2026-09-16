package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sol_site_survey_condition")
public class SiteSurveyConditionDO extends TenantBaseDO {
    @TableId private Long id;
    private Long surveyId;
    private String conditionType;
    private String conditionCode;
    private Integer sortOrder;
}
