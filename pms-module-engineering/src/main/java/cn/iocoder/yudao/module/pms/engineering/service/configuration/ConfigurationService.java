package cn.iocoder.yudao.module.pms.engineering.service.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;

import jakarta.validation.Valid;

/**
 * PMS 配置调试 Service 接口（FR-ENG-023）。
 * <p>
 * 状态流转：0 待调试 → 1 进行中 → 2 已完成；0 待调试 → 3 异常。
 */
public interface ConfigurationService {

    Long createConfiguration(@Valid ConfigurationSaveReqVO createReqVO);

    void updateConfiguration(@Valid ConfigurationSaveReqVO updateReqVO);

    void deleteConfiguration(Long id);

    ConfigurationDO getConfiguration(Long id);

    PageResult<ConfigurationDO> getConfigurationPage(ConfigurationPageReqVO pageReqVO);

    /**
     * 开始调试：待调试(0) → 进行中(1)
     */
    void startConfiguration(Long id);

    /**
     * 完成调试：进行中(1) → 已完成(2)
     */
    void completeConfiguration(Long id);

    /**
     * 标记异常：待调试(0) → 异常(3)
     */
    void markAbnormal(Long id);
}
