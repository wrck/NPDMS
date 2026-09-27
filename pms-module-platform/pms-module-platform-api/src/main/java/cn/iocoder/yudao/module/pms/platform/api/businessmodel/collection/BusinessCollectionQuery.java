package cn.iocoder.yudao.module.pms.platform.api.businessmodel.collection;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;

import java.util.List;

/** 关系成员受控枚举：按关系身份与受控范围读取，支持游标续读。 */
public record BusinessCollectionQuery(
        String ownerModule,
        String entityType,
        Long entityId,
        String relationCode,
        List<BusinessFieldFilter> scopeFilters,
        int pageSize,
        String cursor) {
}
