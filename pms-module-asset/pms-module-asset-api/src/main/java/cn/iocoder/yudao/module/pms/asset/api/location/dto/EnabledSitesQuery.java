package cn.iocoder.yudao.module.pms.asset.api.location.dto;

import java.util.Collection;

/** 受信调用方须先核验每个候选站点的业务范围；空集合不返回任何站点。 */
public record EnabledSitesQuery(Long tenantId, Collection<Long> siteIds) { }
