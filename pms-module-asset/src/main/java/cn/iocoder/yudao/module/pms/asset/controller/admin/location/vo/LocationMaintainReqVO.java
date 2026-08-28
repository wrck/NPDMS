package cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressInput;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteInput;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationInput;
import lombok.Data;

@Data
public class LocationMaintainReqVO {

    private Long projectId;
    private AddressInput address;
    private SiteInput site;
    private SiteLocationInput siteLocation;
    private String fallbackLocation;
    private String sourceBusinessType;
    private String sourceBusinessId;
    private String sourceVersion;

    public LocationMaintenanceCommand toCommand() {
        return new LocationMaintenanceCommand(projectId, address, site, siteLocation, fallbackLocation,
                sourceBusinessType, sourceBusinessId, sourceVersion);
    }

}
