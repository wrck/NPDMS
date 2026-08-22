package cn.iocoder.yudao.module.pms.asset.api.location.dto;

public record LocationMaintenanceCommand(
        Long projectId,
        AddressInput address,
        SiteInput site,
        SiteLocationInput siteLocation,
        String fallbackLocation,
        String sourceBusinessType,
        String sourceBusinessId,
        String sourceVersion) {
}
