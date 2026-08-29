package cn.iocoder.yudao.module.pms.cutover.dal.mysql.configuration.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverConfigurationPageQuery extends PageParam {
    private String configurationCode;
    private String configurationName;
    private String statusCode;
}
