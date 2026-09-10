package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 显式权限授权链查询；ID集合只能由System内部前一步查询产生。 */
@Value
@Builder(toBuilder = true)
public class ExplicitPermissionQuery {

    Long tenantId;
    Long userId;
    String permission;
    Integer enabledStatus;
    List<Long> roleIds;
    List<Long> menuIds;
}
