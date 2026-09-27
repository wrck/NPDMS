package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record LocationReferenceDTO(
        String locationResolutionStatus,
        Long addressId,
        Long addressVersion,
        Long siteId,
        Long siteVersion,
        Long siteLocationId,
        Long siteLocationVersion,
        String fallbackLocation) {
}
