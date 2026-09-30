package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigLogRecordApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceConfigLogRecordCommand;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
import cn.iocoder.yudao.module.pms.engineering.service.EngineeringRecordCodeGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 配置调试 Service 实现（FR-ENG-023）。
 * <p>
 * 状态流转：0 待调试 → 1 进行中 → 2 已完成；0 待调试 → 3 异常。
 */
@Service
@Validated
public class ConfigurationServiceImpl implements ConfigurationService {

    @Resource
    private cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceSelectionApi deviceSelectionApi;

    @Resource
    private ConfigurationMapper configurationMapper;
    @Resource
    private EngineeringRecordCodeGenerator recordCodeGenerator;

    @Resource
    private DeviceConfigLogRecordApi deviceConfigLogRecordApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConfiguration(ConfigurationSaveReqVO createReqVO) {
        if (createReqVO.getEquipmentId() != null) {
            deviceSelectionApi.validateSelection(createReqVO.getProjectId(), java.util.List.of(createReqVO.getEquipmentId()));
        }
        ConfigurationDO configuration = BeanUtils.toBean(createReqVO, ConfigurationDO.class);
        configuration.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                EngineeringRecordCodeGenerator.CONFIGURATION, configurationMapper));
        configuration.setStatus(0); // 状态只由现有动作接口推进
        if (configuration.getVersion() == null) {
            configuration.setVersion(0L);
        }
        configurationMapper.insert(configuration);
        archiveConfigLog(configuration, null);
        return configuration.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfiguration(ConfigurationSaveReqVO updateReqVO) {
        ConfigurationDO existing = validateConfigurationExists(updateReqVO.getId());
        Long equipmentId = updateReqVO.getEquipmentId() != null ? updateReqVO.getEquipmentId() : existing.getEquipmentId();
        if (equipmentId != null) {
            deviceSelectionApi.validateSelection(updateReqVO.getProjectId(), java.util.List.of(equipmentId));
        }
        validateStatus(existing, 0, 1, 3);
        validateVersion(existing, updateReqVO.getVersion());
        ConfigurationDO update = BeanUtils.toBean(updateReqVO, ConfigurationDO.class);
        update.setStatus(existing.getStatus());
        update.setVersion(existing.getVersion());
        updateRecord(update);
        archiveConfigLog(update, existing.getConfigLogUrl());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfiguration(Long id) {
        ConfigurationDO existing = validateConfigurationExists(id);
        validateStatus(existing, 0, 1, 3);
        configurationMapper.deleteById(id);
    }

    @Override
    public ConfigurationDO getConfiguration(Long id) {
        return configurationMapper.selectById(id);
    }

    @Override
    public PageResult<ConfigurationDO> getConfigurationPage(ConfigurationPageReqVO pageReqVO) {
        return configurationMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startConfiguration(Long id) {
        ConfigurationDO configuration = validateConfigurationExists(id);
        validateStatus(configuration, 0); // 待调试 → 进行中
        if (configuration.getDebugTime() == null) {
            configuration.setDebugTime(LocalDateTime.now());
        }
        updateStatus(configuration, 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeConfiguration(Long id) {
        ConfigurationDO configuration = validateConfigurationExists(id);
        validateStatus(configuration, 1); // 进行中 → 已完成
        updateStatus(configuration, 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAbnormal(Long id) {
        ConfigurationDO configuration = validateConfigurationExists(id);
        validateStatus(configuration, 0); // 待调试 → 异常
        updateStatus(configuration, 3);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 配置 Log 归档设备档案（EXE-03：手动上传为独立合法来源）。
     * 上传了 Log 且设备已关联时写入 ast_device_config_log，同一 Log 地址不重复记录；
     * 设备档案 Owner 校验失败随当前事务回滚，不伪装成功。
     */
    private void archiveConfigLog(ConfigurationDO configuration, String previousConfigLogUrl) {
        String configLogUrl = configuration.getConfigLogUrl();
        if (configLogUrl == null || configLogUrl.isBlank()
                || configLogUrl.equals(previousConfigLogUrl)
                || configuration.getEquipmentId() == null) {
            return;
        }
        DeviceConfigLogRecordCommand command = new DeviceConfigLogRecordCommand(
                configuration.getEquipmentId(), "MANUAL_UPLOAD", "PMS", null,
                configLogUrl, null, "配置调试 " + configuration.getCode() + " 手动上传");
        deviceConfigLogRecordApi.recordConfigLog(command);
    }

    private ConfigurationDO validateConfigurationExists(Long id) {
        ConfigurationDO configuration = configurationMapper.selectById(id);
        if (configuration == null) {
            throw exception(CONFIGURATION_NOT_EXISTS);
        }
        return configuration;
    }

    private void validateVersion(ConfigurationDO configuration, Integer version) {
        if (version != null && !Objects.equals(configuration.getVersion(), version.longValue())) {
            throw exception(CONFIGURATION_VERSION_NOT_MATCH);
        }
    }

    private void validateStatus(ConfigurationDO configuration, int... allowedStatuses) {
        for (int allowed : allowedStatuses) {
            if (Objects.equals(configuration.getStatus(), allowed)) {
                return;
            }
        }
        throw exception(CONFIGURATION_STATUS_INVALID);
    }

    private void updateStatus(ConfigurationDO configuration, int newStatus) {
        configuration.setStatus(newStatus);
        updateRecord(configuration);
    }

    private void updateRecord(ConfigurationDO configuration) {
        if (configurationMapper.updateById(configuration) != 1) {
            throw exception(CONFIGURATION_VERSION_NOT_MATCH);
        }
    }
}
