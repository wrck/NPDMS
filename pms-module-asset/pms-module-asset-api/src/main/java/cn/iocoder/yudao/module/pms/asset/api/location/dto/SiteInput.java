package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record SiteInput(
        Long id,
        Integer expectedVersion,
        String code,
        String name,
        Long customerId,
        String siteType) {
}
