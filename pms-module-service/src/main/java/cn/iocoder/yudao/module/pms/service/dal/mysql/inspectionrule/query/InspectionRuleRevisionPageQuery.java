package cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionRuleRevisionPageQuery extends PageParam {

    private Long tenantId;
    private String detectionId;
    private String ruleNameKeyword;
    private String categoryCode;
    private String severityCode;
    private String productTypeCode;
    private String statusCode;

    public long getOffset() {
        return (long) (getPageNo() - 1) * getPageSize();
    }
}
