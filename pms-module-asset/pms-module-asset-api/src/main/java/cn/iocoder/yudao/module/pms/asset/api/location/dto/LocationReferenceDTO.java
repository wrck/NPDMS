package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record LocationReferenceDTO(
        String locationResolutionStatus,
        Long addressId,
        Integer addressVersion,
        Long siteId,
        Integer siteVersion,
        Long siteLocationId,
        Integer siteLocationVersion,
        String fallbackLocation) {
}
