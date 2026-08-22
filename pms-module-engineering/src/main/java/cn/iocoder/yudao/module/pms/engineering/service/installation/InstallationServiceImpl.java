package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.api.location.AssetLocationApi;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.EquipmentLocationEffectiveCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import cn.iocoder.yudao.module.pms.asset.api.location.dto.SiteLocationRespDTO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 硬件安装 Service 实现（FR-ENG-022）。
 * <p>
 * 状态流转：0 待安装 → 1 进行中 → 2 已完成；0 待安装 → 3 异常。
 */
@Service
@Validated
public class InstallationServiceImpl implements InstallationService {

    @Resource
    private InstallationMapper installationMapper;
    @Resource
    private EngineeringLocationFactService locationFactService;
    @Resource
    private AssetLocationApi assetLocationApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInstallation(InstallationSaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        InstallationDO installation = BeanUtils.toBean(createReqVO, InstallationDO.class);
        installation.setStatus(0); // 状态只能通过动作接口流转
        if (installation.getVersion() == null) {
            installation.setVersion(0);
        }
        installationMapper.insert(installation);
        applyLocation(installation, createReqVO.getInstallLocation(), createReqVO.getLocationMaintenance(), 0);
        installationMapper.updateById(installation);
        return installation.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInstallation(InstallationSaveReqVO updateReqVO) {
        InstallationDO existing = validateInstallationExists(updateReqVO.getId());
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        if (Objects.equals(existing.getStatus(), 2)) {
            throw exception(INSTALLATION_STATUS_INVALID);
        }
        InstallationDO update = BeanUtils.toBean(updateReqVO, InstallationDO.class);
        update.setStatus(existing.getStatus());
        applyLocation(update, updateReqVO.getInstallLocation(), updateReqVO.getLocationMaintenance(),
                existing.getVersion() + 1);
        installationMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInstallation(Long id) {
        validateInstallationExists(id);
        installationMapper.deleteById(id);
    }

    @Override
    public InstallationDO getInstallation(Long id) {
        return installationMapper.selectById(id);
    }

    @Override
    public PageResult<InstallationDO> getInstallationPage(InstallationPageReqVO pageReqVO) {
        return installationMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startInstallation(Long id) {
        InstallationDO installation = validateInstallationExists(id);
        validateStatus(installation, 0); // 待安装 → 进行中
        if (installation.getInstallTime() == null) {
            installation.setInstallTime(LocalDateTime.now());
        }
        updateStatus(installation, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeInstallation(Long id) {
        InstallationDO installation = validateInstallationExists(id);
        validateStatus(installation, 1); // 进行中 → 已完成
        validateEffectiveLocation(installation);
        LocalDateTime effectiveFrom = installation.getInstallTime() == null
                ? LocalDateTime.now() : installation.getInstallTime();
        InstallationDO current = installationMapper.selectCurrentByEquipmentId(installation.getEquipmentId());
        if (current != null && !Objects.equals(current.getId(), installation.getId())) {
            if (current.getEffectiveFrom() != null && !effectiveFrom.isAfter(current.getEffectiveFrom())) {
                throw exception(INSTALLATION_EFFECTIVE_TIME_INVALID);
            }
            current.setEffectiveTo(effectiveFrom);
            installationMapper.updateById(current);
        }
        installation.setEffectiveFrom(effectiveFrom);
        installation.setEffectiveTo(null);
        updateStatus(installation, 2);
        assetLocationApi.effectEquipmentLocation(new EquipmentLocationEffectiveCommand(
                installation.getEquipmentId(), installation.getId(), installation.getSiteId(),
                installation.getSiteLocationId(), installation.getInstallLocation(),
                installation.getLocationResolutionStatus(), installation.getLocationSnapshot(), effectiveFrom));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAbnormal(Long id) {
        InstallationDO installation = validateInstallationExists(id);
        validateStatus(installation, 0); // 待安装 → 异常
        updateStatus(installation, 3);
    }

    // ==================== 内部工具方法 ====================

    private InstallationDO validateInstallationExists(Long id) {
        InstallationDO installation = installationMapper.selectById(id);
        if (installation == null) {
            throw exception(INSTALLATION_NOT_EXISTS);
        }
        return installation;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        InstallationDO existing = installationMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(INSTALLATION_CODE_DUPLICATE);
        }
    }

    private void validateVersion(InstallationDO installation, Integer version) {
        if (version != null && !Objects.equals(installation.getVersion(), version)) {
            throw exception(INSTALLATION_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(InstallationDO installation, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(installation.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(INSTALLATION_STATUS_INVALID);
    }

    private void updateStatus(InstallationDO installation, int newStatus) {
        installation.setStatus(newStatus);
        installation.setVersion(installation.getVersion() + 1);
        installationMapper.updateById(installation);
    }

    private void applyLocation(InstallationDO installation, String fallbackLocation,
                               LocationMaintenanceCommand command, Integer sourceVersion) {
        if (command == null) {
            if (fallbackLocation == null || fallbackLocation.isBlank()) {
                throw exception(INSTALLATION_LOCATION_REQUIRED);
            }
            installation.setLocationResolutionStatus("UNRESOLVED");
            return;
        }
        EngineeringLocationFactService.LocationFact fact = locationFactService.maintain(installation.getProjectId(),
                "INSTALLATION", installation.getId(), sourceVersion, fallbackLocation, command);
        if (!"RESOLVED".equals(fact.resolutionStatus())) {
            throw exception(INSTALLATION_LOCATION_INVALID);
        }
        installation.setAddressId(fact.addressId());
        installation.setAddressVersion(fact.addressVersion());
        installation.setSiteId(fact.siteId());
        installation.setSiteVersion(fact.siteVersion());
        installation.setSiteLocationId(fact.siteLocationId());
        installation.setSiteLocationVersion(fact.siteLocationVersion());
        installation.setLocationResolutionStatus(fact.resolutionStatus());
        installation.setAddressSnapshot(fact.addressSnapshot());
        installation.setLocationSnapshot(fact.locationSnapshot());
    }

    private void validateEffectiveLocation(InstallationDO installation) {
        if (!"RESOLVED".equals(installation.getLocationResolutionStatus())) {
            if (installation.getInstallLocation() == null || installation.getInstallLocation().isBlank()) {
                throw exception(INSTALLATION_LOCATION_REQUIRED);
            }
            return;
        }
        assetLocationApi.validateSites(java.util.List.of(installation.getSiteId()));
        if (installation.getSiteLocationId() == null) {
            return;
        }
        SiteLocationRespDTO location = assetLocationApi.getSiteLocation(installation.getSiteLocationId(),
                installation.getSiteLocationVersion());
        if (!Objects.equals(location.siteId(), installation.getSiteId())
                || !CommonStatusEnum.isEnable(location.status())) {
            throw exception(INSTALLATION_LOCATION_INVALID);
        }
    }
}
