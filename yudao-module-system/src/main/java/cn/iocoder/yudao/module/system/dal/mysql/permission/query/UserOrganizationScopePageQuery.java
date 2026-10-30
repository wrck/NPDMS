package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserOrganizationScopePageQuery extends PageParam {
    private Long userId;
    private Integer status;
}
