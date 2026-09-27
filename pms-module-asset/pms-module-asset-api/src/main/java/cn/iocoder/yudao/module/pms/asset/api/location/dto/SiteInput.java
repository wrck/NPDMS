package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record SiteInput(
        Long id,
        Long expectedVersion,
        String code,
        String name,
        Long customerId,
        String siteType) {
}
