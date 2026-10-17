package cn.iocoder.yudao.module.pms.asset.api.location.dto;

/** Trusted caller must establish customer access before querying its sites. */
public record CustomerSiteQuery(Long tenantId, Long customerId) { }
