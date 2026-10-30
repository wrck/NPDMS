package cn.iocoder.yudao.module.pms.platform.api.businessmodel.result;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;

/** 结果查询：当前结果、指定结果或存量枚举。 */
public record BusinessResultQuery(String resultType, EntityRef objectRef, String resultId, boolean onlyValid) {
}
