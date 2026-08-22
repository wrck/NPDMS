package cn.iocoder.yudao.module.pms.asset.service.location;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AddressRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.AreaDepartmentMappingRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteRespDTO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.location.vo.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AddressDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.AreaDepartmentMappingDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.SiteDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AddressMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.AreaDepartmentMappingMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.SiteMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.asset.service.location.AreaDepartmentMappingServiceImpl.SERVICE_OFFICE;

@Service
@RequiredArgsConstructor
public class AssetLocationAdminServiceImpl implements AssetLocationAdminService {

    private static final Set<String> AREA_LEVELS = Set.of("COUNTRY", "PROVINCE", "CITY", "DISTRICT");

    private final AddressMapper addressMapper;
    private final SiteMapper siteMapper;
    private final AreaDepartmentMappingMapper mappingMapper;
    private final DeptApi deptApi;

    @Override
    public PageResult<AddressRespDTO> getAddressPage(AddressPageReqVO reqVO) {
        PageResult<AddressDO> page = addressMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toAddressResp).toList(), page.getTotal());
    }

    @Override
    public PageResult<SiteRespDTO> getSitePage(SitePageReqVO reqVO) {
        PageResult<SiteDO> page = siteMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toSiteResp).toList(), page.getTotal());
    }

    @Override
    public PageResult<AreaDepartmentMappingRespDTO> getMappingPage(AreaDepartmentMappingPageReqVO reqVO) {
        PageResult<AreaDepartmentMappingDO> page = mappingMapper.selectPage(reqVO);
        return new PageResult<>(page.getList().stream().map(this::toMappingResp).toList(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveMapping(AreaDepartmentMappingSaveReqVO reqVO) {
        validateMapping(reqVO);
        AreaDepartmentMappingDO entity = new AreaDepartmentMappingDO();
        entity.setAreaCode(reqVO.getAreaCode());
        entity.setAreaLevel(reqVO.getAreaLevel());
        entity.setMappingType(SERVICE_OFFICE);
        entity.setDepartmentCode(reqVO.getDepartmentCode());
        entity.setEffectiveFrom(reqVO.getEffectiveFrom());
        entity.setEffectiveTo(reqVO.getEffectiveTo());
        entity.setStatus(reqVO.getStatus());
        if (reqVO.getId() == null) {
            entity.setVersion(0);
            mappingMapper.insert(entity);
            return entity.getId();
        }
        AreaDepartmentMappingDO existing = mappingMapper.selectById(reqVO.getId());
        if (existing == null) {
            throw exception(AST_AREA_DEPARTMENT_MAPPING_NOT_EXISTS);
        }
        if (!Objects.equals(existing.getVersion(), reqVO.getExpectedVersion())) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
        entity.setId(existing.getId());
        entity.setVersion(existing.getVersion() + 1);
        if (mappingMapper.updateByIdAndVersion(entity, existing.getVersion()) == 0) {
            throw exception(AST_LOCATION_VERSION_CONFLICT);
        }
        return entity.getId();
    }

    private void validateMapping(AreaDepartmentMappingSaveReqVO reqVO) {
        if (!AREA_LEVELS.contains(reqVO.getAreaLevel()) || reqVO.getEffectiveFrom() == null
                || (!CommonStatusEnum.isEnable(reqVO.getStatus())
                && !CommonStatusEnum.isDisable(reqVO.getStatus()))) {
            throw exception(AST_AREA_DEPARTMENT_MAPPING_INVALID);
        }
        if (reqVO.getEffectiveTo() != null && !reqVO.getEffectiveTo().isAfter(reqVO.getEffectiveFrom())) {
            throw exception(AST_AREA_DEPARTMENT_MAPPING_INVALID);
        }
        DeptRespDTO department = getDepartment(reqVO.getDepartmentCode());
        if (department == null || !CommonStatusEnum.isEnable(department.getStatus())) {
            throw exception(AST_AREA_DEPARTMENT_MAPPING_INVALID);
        }
        if (!CommonStatusEnum.isEnable(reqVO.getStatus())) {
            return;
        }
        List<AreaDepartmentMappingDO> existingMappings = mappingMapper.selectListByArea(
                reqVO.getAreaCode(), reqVO.getAreaLevel(), SERVICE_OFFICE);
        boolean overlaps = existingMappings.stream()
                .filter(item -> !Objects.equals(item.getId(), reqVO.getId()))
                .filter(item -> CommonStatusEnum.isEnable(item.getStatus()))
                .anyMatch(item -> overlaps(reqVO.getEffectiveFrom(), reqVO.getEffectiveTo(),
                        item.getEffectiveFrom(), item.getEffectiveTo()));
        if (overlaps) {
            throw exception(AST_AREA_DEPARTMENT_MAPPING_OVERLAP);
        }
    }

    private boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd,
                             LocalDateTime rightStart, LocalDateTime rightEnd) {
        return (rightEnd == null || leftStart.isBefore(rightEnd))
                && (leftEnd == null || rightStart.isBefore(leftEnd));
    }

    private AreaDepartmentMappingRespDTO toMappingResp(AreaDepartmentMappingDO entity) {
        DeptRespDTO department = getDepartment(entity.getDepartmentCode());
        return new AreaDepartmentMappingRespDTO(entity.getId(), entity.getAreaCode(), entity.getAreaLevel(),
                entity.getMappingType(), entity.getDepartmentCode(), department == null ? null : department.getName(),
                entity.getEffectiveFrom(), entity.getEffectiveTo(), entity.getVersion());
    }

    private DeptRespDTO getDepartment(String departmentCode) {
        try {
            return deptApi.getDeptByCode(departmentCode);
        } catch (ServiceException ignored) {
            return null;
        }
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

}
