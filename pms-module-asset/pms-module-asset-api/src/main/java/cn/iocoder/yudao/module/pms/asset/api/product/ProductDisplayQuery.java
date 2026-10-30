package cn.iocoder.yudao.module.pms.asset.api.product;

import java.util.Set;

/** 产品清单展示，沿用产品目录按ID倒序取首条精确编码的读取口径。 */
public record ProductDisplayQuery(Long tenantId, Set<String> productCodes) { }
