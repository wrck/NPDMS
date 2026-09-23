package cn.iocoder.yudao.module.pms.asset.api.location;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AddressDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.LocationSourceMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AddressMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.LocationSourceMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.pms.asset.enums.LocationMatchStatus;
import cn.iocoder.yudao.module.pms.asset.enums.LocationResolutionStatus;
import cn.iocoder.yudao.module.pms.asset.service.location.AreaDepartmentMappingService;
import cn.iocoder.yudao.module.pms.asset.service.location.SiteLocationTreeService;
import cn.iocoder.yudao.module.pms.asset.service.location.DeviceLocationEffectiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;

@Service
@RequiredArgsConstructor
public class AssetLocationApiImpl implements AssetLocationApi {

    private static final String SOURCE_SYSTEM_PMS = "PMS";

    private final cn.iocoder.yudao.module.pms.customer.api.query.CustomerQueryApi customerQueryApi;
    private final cn.iocoder.yudao.module.pms.asset.service.location.LocationCodeService locationCodeService;
    private final AddressMapper addressMapper;
    private final SiteMapper siteMapper;
    private final LocationSourceMappingMapper sourceMappingMapper;
    private final SiteLocationTreeService siteLocationTreeService;
    private final AreaDepartmentMappingService areaDepartmentMappingService;
    private final DeviceLocationEffectiveService deviceLocationEffectiveService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocationReferenceDTO maintain(LocationMaintenanceCommand command) {
        if (command == null) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
        LocationSourceMappingDO existingMapping = findSourceMapping(command);
        if (isReplay(existingMapping, command)) {
            validateReplayReferences(existingMapping, command);
            return toReference(existingMapping, null, command.fallbackLocation());
        }
        if (command.address() == null && command.site() == null && command.siteLocation() == null) {
            LocationReferenceDTO unresolved = new LocationReferenceDTO(LocationResolutionStatus.UNRESOLVED.name(),
                    null, null, null, null, null, null, command.fallbackLocation());
            maintainSourceMapping(existingMapping, command, unresolved);
            return unresolved;
        }

        AddressDO address = command.address() == null ? null : maintainAddress(command.address());
        SiteDO site = command.site() == null ? null : maintainSite(command.site(), address);
        if (site == null && (command.siteLocation() != null || hasAppendableLocation(command.extraSiteLocations()))) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
        SiteLocationDO location = command.siteLocation() == null ? null
                : siteLocationTreeService.maintain(site.getId(), command.siteLocation());
        SiteLocationDO chainEnd = maintainExtraSiteLocations(site, location, command.extraSiteLocations());
        SiteLocationDO referenceLocation = chainEnd != null ? chainEnd : location;
        if (site == null) {
            LocationReferenceDTO addressOnly = new LocationReferenceDTO(LocationResolutionStatus.RESOLVED.name(),
                    address.getId(), address.getVersion(), null, null, null, null, command.fallbackLocation());
            maintainSourceMapping(existingMapping, command, addressOnly);
            return addressOnly;
        }
        LocationReferenceDTO result = new LocationReferenceDTO(LocationResolutionStatus.RESOLVED.name(),
                address != null ? address.getId() : site.getAddressId(),
                address != null ? address.getVersion() : getAddress(site.getAddressId(), null).version(),
                site.getId(), site.getVersion(), referenceLocation == null ? null : referenceLocation.getId(),
                referenceLocation == null ? null : referenceLocation.getVersion(), command.fallbackLocation());
        maintainSourceMapping(existingMapping, command, result);
        return result;
    }

    /**
     * 按输入顺序把附加位置依次追加为上一级的下级；编码由站点位置树服务自动生成。
     * 返回追加后的链路末端（最深层位置），无有效追加时返回入参 parent；地点引用即指向该末端节点。
     */
    private SiteLocationDO maintainExtraSiteLocations(SiteDO site, SiteLocationDO parent, List<SiteLocationInput> extras) {
        if (extras == null || extras.isEmpty()) {
            return parent;
        }
        SiteLocationDO last = parent;
        for (SiteLocationInput extra : extras) {
            if (extra == null || extra.name() == null || extra.name().isBlank()) {
                continue;
            }
            last = siteLocationTreeService.maintain(site.getId(), new SiteLocationInput(null, null,
                    last == null ? null : last.getId(), null, extra.name(), extra.locationType(), 0));
        }
        return last;
    }

    private boolean hasAppendableLocation(List<SiteLocationInput> extras) {
        return extras != null && extras.stream()
                .anyMatch(extra -> extra != null && hasText(extra.name()));
    }

    @Override
    public AddressRespDTO getAddress(Long addressId, Integer expectedVersion) {
        AddressDO entity = addressMapper.selectById(addressId);
        if (entity == null) {
            throw exception(AST_ADDRESS_NOT_EXISTS);
        }
        validateVersion(entity.getVersion(), expectedVersion);
        return toAddressResp(entity);
    }

    @Override
    public SiteRespDTO getSite(Long siteId, Integer expectedVersion) {
        SiteDO entity = siteMapper.selectById(siteId);
        if (entity == null) {
            throw exception(AST_SITE_NOT_EXISTS);
        }
        validateVersion(entity.getVersion(), expectedVersion);
        return toSiteResp(entity);
    }

    @Override
    public SiteLocationRespDTO getSiteLocation(Long locationId, Integer expectedVersion) {
        return toSiteLocationResp(siteLocationTreeService.get(locationId, expectedVersion));
    }

    @Override
    public List<SiteLocationRespDTO> getLocationTree(Long siteId) {
        getSite(siteId, null);
        return siteLocationTreeService.getTree(siteId).stream().map(this::toSiteLocationResp).toList();
    }

    @Override
    public AreaDepartmentMappingRespDTO resolveDepartment(String areaCode, String areaLevel) {
        return areaDepartmentMappingService.resolve(areaCode, areaLevel);
    }

    @Override
    public void validateSites(Collection<Long> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return;
        }
        List<SiteDO> sites = siteMapper.selectListByIds(siteIds);
        long validCount = sites.stream().filter(site -> CommonStatusEnum.isEnable(site.getStatus())).count();
        if (validCount != siteIds.stream().distinct().count()) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
    }

    @Override
    public void effectEquipmentLocation(EquipmentLocationEffectiveCommand command) {
        deviceLocationEffectiveService.effectOrThrow(command);
    }

    private AddressDO maintainAddress(AddressInput input) {
        if (input.id() == null) {
            AddressDO entity = copyAddress(input);
            entity.setStatus(CommonStatusEnum.ENABLE.getStatus());
            entity.setVersion(0);
            addressMapper.insert(entity);
            return entity;
        }
        AddressDO existing = addressMapper.selectById(input.id());
        if (existing == null) {
            throw exception(AST_ADDRESS_NOT_EXISTS);
        }
        validateVersion(existing.getVersion(), input.expectedVersion());
        if (isReferenceOnly(input)) {
            return existing;
        }
        AddressDO update = copyAddress(input);
        update.setId(existing.getId());
        update.setVersion(existing.getVersion() + 1);
        if (addressMapper.updateByIdAndVersion(update, existing.getVersion()) == 0) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
        update.setStatus(existing.getStatus());
        return update;
    }

    private SiteDO maintainSite(SiteInput input, AddressDO maintainedAddress) {
        if (input.id() == null) {
            if (maintainedAddress == null) {
                throw exception(AST_LOCATION_REFERENCE_INVALID);
            }
            SiteDO entity = copySite(input);
            if (!hasText(input.code())) {
                String prefix = "PUBLIC-S-";
                if (input.customerId() != null) {
                    var customer = customerQueryApi.getCustomer(input.customerId());
                    if (customer == null || !hasText(customer.code())) {
                        throw exception(AST_LOCATION_REFERENCE_INVALID);
                    }
                    prefix = customer.code() + "-S-";
                }
                String code;
                do {
                    code = locationCodeService.next(prefix);
                } while (siteMapper.selectByCode(code) != null);
                entity.setCode(code);
            }
            validateSiteCodeUnique(null, entity.getCode());
            entity.setAddressId(maintainedAddress.getId());
            entity.setStatus(CommonStatusEnum.ENABLE.getStatus());
            entity.setVersion(0);
            siteMapper.insert(entity);
            return entity;
        }
        SiteDO existing = siteMapper.selectById(input.id());
        if (existing == null) {
            throw exception(AST_SITE_NOT_EXISTS);
        }
        validateVersion(existing.getVersion(), input.expectedVersion());
        if (isReferenceOnly(input) && (maintainedAddress == null
                || Objects.equals(existing.getAddressId(), maintainedAddress.getId()))) {
            return existing;
        }
        if (hasText(input.code()) && !Objects.equals(input.code(), existing.getCode())) {
            throw exception(AST_LOCATION_REFERENCE_INVALID);
        }
        SiteDO update = isReferenceOnly(input) ? copySite(existing) : copySite(input);
        update.setCode(existing.getCode());
        update.setId(existing.getId());
        update.setAddressId(maintainedAddress == null ? existing.getAddressId() : maintainedAddress.getId());
        update.setVersion(existing.getVersion() + 1);
        if (siteMapper.updateByIdAndVersion(update, existing.getVersion()) == 0) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
        update.setStatus(existing.getStatus());
        return update;
    }

    private void validateSiteCodeUnique(Long currentId, String code) {
        SiteDO duplicate = siteMapper.selectByCode(code);
        if (duplicate != null && !Objects.equals(duplicate.getId(), currentId)) {
            throw exception(AST_SITE_CODE_DUPLICATE);
        }
    }

    private LocationSourceMappingDO findSourceMapping(LocationMaintenanceCommand command) {
        if (!hasText(command.sourceBusinessType()) || !hasText(command.sourceBusinessId())) {
            return null;
        }
        return sourceMappingMapper.selectBySourceKey(SOURCE_SYSTEM_PMS,
                command.sourceBusinessType(), command.sourceBusinessId());
    }

    private boolean isReplay(LocationSourceMappingDO mapping, LocationMaintenanceCommand command) {
        return mapping != null && Objects.equals(mapping.getSourceVersion(), command.sourceVersion());
    }

    private void validateReplayReferences(LocationSourceMappingDO mapping, LocationMaintenanceCommand command) {
        if (command.address() != null && command.address().id() != null
                && !Objects.equals(command.address().id(), mapping.getAddressId())) {
            throw exception(AST_LOCATION_SOURCE_CONFLICT);
        }
        if (command.site() != null && command.site().id() != null
                && !Objects.equals(command.site().id(), mapping.getSiteId())) {
            throw exception(AST_LOCATION_SOURCE_CONFLICT);
        }
        if (command.siteLocation() != null && command.siteLocation().id() != null
                && !Objects.equals(command.siteLocation().id(), mapping.getSiteLocationId())) {
            throw exception(AST_LOCATION_SOURCE_CONFLICT);
        }
    }

    private void maintainSourceMapping(LocationSourceMappingDO existing, LocationMaintenanceCommand command,
                                       LocationReferenceDTO reference) {
        if (!hasText(command.sourceBusinessType()) || !hasText(command.sourceBusinessId())) {
            return;
        }
        LocationSourceMappingDO entity = new LocationSourceMappingDO();
        entity.setSourceSystem(SOURCE_SYSTEM_PMS);
        entity.setObjectType(command.sourceBusinessType());
        entity.setSourceKey(command.sourceBusinessId());
        entity.setSourceVersion(command.sourceVersion());
        entity.setAddressId(reference.addressId());
        entity.setSiteId(reference.siteId());
        entity.setSiteLocationId(reference.siteLocationId());
        entity.setMatchStatus(reference.siteId() == null ? LocationMatchStatus.PENDING.name()
                : LocationMatchStatus.MATCHED.name());
        entity.setLocationResolutionStatus(reference.locationResolutionStatus());
        entity.setLastSyncedAt(LocalDateTime.now());
        if (existing == null) {
            entity.setVersion(0);
            sourceMappingMapper.insert(entity);
            return;
        }
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion() + 1);
        if (sourceMappingMapper.updateByIdAndVersion(entity, existing.getVersion()) == 0) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
    }

    private LocationReferenceDTO toReference(LocationSourceMappingDO mapping, Long locationId, String fallback) {
        Integer addressVersion = mapping.getAddressId() == null ? null : getAddress(mapping.getAddressId(), null).version();
        Integer siteVersion = mapping.getSiteId() == null ? null : getSite(mapping.getSiteId(), null).version();
        Long replayLocationId = locationId == null ? mapping.getSiteLocationId() : locationId;
        Integer locationVersion = replayLocationId == null ? null : getSiteLocation(replayLocationId, null).version();
        return new LocationReferenceDTO(mapping.getLocationResolutionStatus(), mapping.getAddressId(), addressVersion,
                mapping.getSiteId(), siteVersion, replayLocationId, locationVersion, fallback);
    }

    private void validateVersion(Integer actualVersion, Integer expectedVersion) {
        if (expectedVersion != null && !Objects.equals(actualVersion, expectedVersion)) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
    }

    private AddressDO copyAddress(AddressInput input) {
        AddressDO entity = new AddressDO();
        entity.setCountryCode(input.countryCode());
        entity.setCountryName(input.countryName());
        entity.setProvinceCode(input.provinceCode());
        entity.setProvinceName(input.provinceName());
        entity.setCityCode(input.cityCode());
        entity.setCityName(input.cityName());
        entity.setDistrictCode(input.districtCode());
        entity.setDistrictName(input.districtName());
        entity.setDetailAddress(input.detailAddress());
        entity.setFullAddress(input.fullAddress());
        entity.setLongitude(input.longitude());
        entity.setLatitude(input.latitude());
        entity.setNormalizedAddress(input.normalizedAddress());
        entity.setAddressFingerprint(input.addressFingerprint());
        return entity;
    }

    private SiteDO copySite(SiteInput input) {
        SiteDO entity = new SiteDO();
        entity.setCode(input.code());
        entity.setName(input.name());
        entity.setCustomerId(input.customerId());
        entity.setSiteType(input.siteType());
        return entity;
    }

    private SiteDO copySite(SiteDO source) {
        SiteDO entity = new SiteDO();
        entity.setCode(source.getCode());
        entity.setName(source.getName());
        entity.setCustomerId(source.getCustomerId());
        entity.setSiteType(source.getSiteType());
        return entity;
    }

    private AddressRespDTO toAddressResp(AddressDO entity) {
        return new AddressRespDTO(entity.getId(), entity.getCountryCode(), entity.getCountryName(),
                entity.getProvinceCode(), entity.getProvinceName(), entity.getCityCode(), entity.getCityName(),
                entity.getDistrictCode(), entity.getDistrictName(), entity.getDetailAddress(), entity.getFullAddress(),
                entity.getLongitude(), entity.getLatitude(), entity.getStatus(), entity.getVersion());
    }

    private SiteRespDTO toSiteResp(SiteDO entity) {
        return new SiteRespDTO(entity.getId(), entity.getCode(), entity.getName(), entity.getCustomerId(),
                entity.getAddressId(), entity.getSiteType(), entity.getStatus(), entity.getVersion());
    }

    private SiteLocationRespDTO toSiteLocationResp(SiteLocationDO entity) {
        return new SiteLocationRespDTO(entity.getId(), entity.getSiteId(), entity.getParentId(), entity.getCode(),
                entity.getName(), entity.getLocationType(), entity.getTreePath(), entity.getTreeDepth(),
                entity.getTreeSort(), entity.getStatus(), entity.getVersion());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isReferenceOnly(AddressInput input) {
        return input.countryCode() == null && input.countryName() == null
                && input.provinceCode() == null && input.provinceName() == null
                && input.cityCode() == null && input.cityName() == null
                && input.districtCode() == null && input.districtName() == null
                && input.detailAddress() == null && input.fullAddress() == null
                && input.longitude() == null && input.latitude() == null
                && input.normalizedAddress() == null && input.addressFingerprint() == null;
    }

    private boolean isReferenceOnly(SiteInput input) {
        return input.code() == null && input.name() == null && input.customerId() == null && input.siteType() == null;
    }

}
