package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import java.util.List;

/** 受控分页查询：由明确读取场景发起，服务端确定租户、权限与数据范围。 */
public record BusinessEntityPageQuery(
        String sceneCode,
        String ownerModule,
        String entityType,
        List<BusinessFieldFilter> filters,
        int pageSize,
        String cursor) {
}
