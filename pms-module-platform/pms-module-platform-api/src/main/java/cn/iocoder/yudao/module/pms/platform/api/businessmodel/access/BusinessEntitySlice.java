package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;

import java.util.List;

/** 分页第一页不能冒充全集：PARTIAL 必须携带 nextCursor，UNAVAILABLE 必须给出原因。 */
public record BusinessEntitySlice(
        List<BusinessEntityData> members,
        String nextCursor,
        Completeness completeness,
        String unavailableReason) {
}
