package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** FR-ENG-001：原平台租户隔离的现场工勘历史分页，不将旧项目ID解释为现代项目范围。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteSurveyPageQuery extends PageParam {
    private Long projectId;
    private String code;
    private String name;
    private Integer status;
    private Long surveyorUserId;
}
