package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.installation.vo.InstallationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;

import jakarta.validation.Valid;

/**
 * PMS 硬件安装 Service 接口（FR-ENG-022）。
 * <p>
 * 状态流转：0 待安装 → 1 进行中 → 2 已完成；0 待安装 → 3 异常。
 */
public interface InstallationService {

    Long createInstallation(@Valid InstallationSaveReqVO createReqVO);

    void updateInstallation(@Valid InstallationSaveReqVO updateReqVO);

    void deleteInstallation(Long id);

    InstallationDO getInstallation(Long id);

    PageResult<InstallationDO> getInstallationPage(InstallationPageReqVO pageReqVO);

    /**
     * 开始安装：待安装(0) → 进行中(1)
     */
    void startInstallation(Long id);

    /**
     * 完成安装：进行中(1) → 已完成(2)
     */
    void completeInstallation(Long id);

    /**
     * 标记异常：待安装(0) → 异常(3)
     */
    void markAbnormal(Long id);
}
