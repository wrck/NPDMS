package cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.sitesurvey.entity.SiteSurveyEntityDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Map;

/** Read-only migration query result, never used by normal survey operations or written to a table. */
@Data
@EqualsAndHashCode(callSuper = true)
public class SiteSurveyImportSource extends SiteSurveyEntityDO {
    private Long formRevisionId;
    private Integer formRevisionVersion;
    private Map<String, Object> formExtraValues;
}
