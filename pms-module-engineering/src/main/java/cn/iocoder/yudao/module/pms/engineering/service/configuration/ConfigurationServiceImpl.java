package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration.ConfigurationMapper;
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
    private ConfigurationMapper configurationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createConfiguration(ConfigurationSaveReqVO createReqVO) {
        validateCodeUnique(createReqVO.getProjectId(), createReqVO.getCode(), null);
        ConfigurationDO configuration = BeanUtils.toBean(createReqVO, ConfigurationDO.class);
        if (configuration.getStatus() == null) {
            configuration.setStatus(0); // 待调试
        }
        if (configuration.getVersion() == null) {
            configuration.setVersion(0);
        }
        configurationMapper.insert(configuration);
        return configuration.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfiguration(ConfigurationSaveReqVO updateReqVO) {
        ConfigurationDO existing = validateConfigurationExists(updateReqVO.getId());
        validateCodeUnique(existing.getProjectId(), updateReqVO.getCode(), updateReqVO.getId());
        validateVersion(existing, updateReqVO.getVersion());
        ConfigurationDO update = BeanUtils.toBean(updateReqVO, ConfigurationDO.class);
        configurationMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfiguration(Long id) {
        validateConfigurationExists(id);
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

    private ConfigurationDO validateConfigurationExists(Long id) {
        ConfigurationDO configuration = configurationMapper.selectById(id);
        if (configuration == null) {
            throw exception(CONFIGURATION_NOT_EXISTS);
        }
        return configuration;
    }

    private void validateCodeUnique(Long projectId, String code, Long excludeId) {
        ConfigurationDO existing = configurationMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && !Objects.equals(existing.getId(), excludeId)) {
            throw exception(CONFIGURATION_CODE_DUPLICATE);
        }
    }

    private void validateVersion(ConfigurationDO configuration, Integer version) {
        if (version != null && !Objects.equals(configuration.getVersion(), version)) {
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
        configuration.setVersion(configuration.getVersion() + 1);
        configurationMapper.updateById(configuration);
    }
}
