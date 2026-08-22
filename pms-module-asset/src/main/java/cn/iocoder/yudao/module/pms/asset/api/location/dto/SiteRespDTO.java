package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record SiteRespDTO(
        Long id,
        String code,
        String name,
        Long customerId,
        Long addressId,
        String siteType,
        Integer status,
        Integer version) {
}
