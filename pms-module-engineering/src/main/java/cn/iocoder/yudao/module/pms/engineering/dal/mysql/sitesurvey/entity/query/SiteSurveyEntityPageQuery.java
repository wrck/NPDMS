package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
public class SiteSurveyEntityPageQuery extends PageParam {
    private Long tenantId;
    private Set<Long> visibleProjectIds;
    private Long projectId;
    private String code;
    private String name;
    private Integer status;
    private Long surveyorUserId;
}
