package cn.iocoder.yudao.module.pms.asset.api.location;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AreaDepartmentMappingRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationReferenceDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;

import java.util.Collection;
import java.util.List;

/**
 * AST 地点跨模块公开契约。
 */
public interface AssetLocationApi {

    LocationReferenceDTO maintain(LocationMaintenanceCommand command);

    AddressRespDTO getAddress(Long addressId, Integer expectedVersion);

    SiteRespDTO getSite(Long siteId, Integer expectedVersion);

    SiteLocationRespDTO getSiteLocation(Long locationId, Integer expectedVersion);

    List<SiteLocationRespDTO> getLocationTree(Long siteId);

    AreaDepartmentMappingRespDTO resolveDepartment(String areaCode, String areaLevel);

    void validateSites(Collection<Long> siteIds);

    void effectEquipmentLocation(EquipmentLocationEffectiveCommand command);

}
