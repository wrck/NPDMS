package cn.iocoder.yudao.module.pms.engineering.service.location;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationReferenceDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将工程实施地点输入转换为可持久化的结构化引用和发生时快照。
 */
@Service
@RequiredArgsConstructor
public class EngineeringLocationFactService {

    private final AssetLocationApi assetLocationApi;

    public LocationFact maintain(Long projectId, String businessType, Long businessId, Integer sourceVersion,
                                 String fallbackLocation, LocationMaintenanceCommand input) {
        LocationMaintenanceCommand command = new LocationMaintenanceCommand(projectId, input.address(), input.site(),
                input.siteLocation(), fallbackLocation, businessType, businessId.toString(),
                String.valueOf(sourceVersion));
        LocationReferenceDTO reference = assetLocationApi.maintain(command);
        AddressRespDTO address = reference.addressId() == null ? null
                : assetLocationApi.getAddress(reference.addressId(), reference.addressVersion());
        SiteRespDTO site = reference.siteId() == null ? null
                : assetLocationApi.getSite(reference.siteId(), reference.siteVersion());
        SiteLocationRespDTO location = reference.siteLocationId() == null ? null
                : assetLocationApi.getSiteLocation(reference.siteLocationId(), reference.siteLocationVersion());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("address", address);
        snapshot.put("site", site);
        snapshot.put("siteLocation", location);
        snapshot.put("fallbackLocation", fallbackLocation);
        return new LocationFact(reference.addressId(), reference.addressVersion(), reference.siteId(),
                reference.siteVersion(), reference.siteLocationId(), reference.siteLocationVersion(),
                reference.locationResolutionStatus(), address == null ? null : JsonUtils.toJsonString(address),
                JsonUtils.toJsonString(snapshot));
    }

    public record LocationFact(
            Long addressId,
            Integer addressVersion,
            Long siteId,
            Integer siteVersion,
            Long siteLocationId,
            Integer siteLocationVersion,
            String resolutionStatus,
            String addressSnapshot,
            String locationSnapshot) {
    }
}
