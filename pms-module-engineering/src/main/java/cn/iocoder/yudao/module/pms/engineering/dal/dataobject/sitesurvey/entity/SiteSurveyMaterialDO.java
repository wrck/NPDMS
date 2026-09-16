package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sol_site_survey_material")
public class SiteSurveyMaterialDO extends TenantBaseDO {
    @TableId @JsonIgnore private Long id;
    @JsonIgnore private Long surveyId;
    private Long deviceId;
    private Long projectId;
    private String sn;
    private String productCode;
    private String productName;
    private String productModel;
    private String reason;
    @JsonIgnore private Integer sortOrder;
}
