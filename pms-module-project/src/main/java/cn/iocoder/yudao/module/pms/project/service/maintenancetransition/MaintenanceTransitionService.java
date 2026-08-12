package cn.iocoder.yudao.module.pms.project.service.maintenancetransition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.maintenancetransition.vo.MaintenanceTransitionSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.maintenancetransition.MaintenanceTransitionDO;

/**
 * 转维保 Service 接口
 */
public interface MaintenanceTransitionService {

    /**
     * 创建转维保
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createMaintenanceTransition(MaintenanceTransitionSaveReqVO createReqVO);

    /**
     * 更新转维保
     *
     * @param updateReqVO 更新信息
     */
    void updateMaintenanceTransition(MaintenanceTransitionSaveReqVO updateReqVO);

    /**
     * 删除转维保
     *
     * @param id 编号
     */
    void deleteMaintenanceTransition(Long id);

    /**
     * 获得转维保分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<MaintenanceTransitionDO> getMaintenanceTransitionPage(MaintenanceTransitionPageReqVO pageReqVO);

    /**
     * 获得转维保
     *
     * @param id 编号
     * @return 转维保
     */
    MaintenanceTransitionDO getMaintenanceTransition(Long id);

    /**
     * 提交（0草稿 → 1待生效）
     *
     * @param id 编号
     */
    void submitMaintenanceTransition(Long id);

    /**
     * 生效（1待生效 → 2生效中）
     * 基于验收时间和维保年限自动生成设备维护期
     *
     * @param id 编号
     */
    void activate(Long id);

    /**
     * 过期（2生效中 → 3已过期）
     *
     * @param id 编号
     */
    void expire(Long id);

    /**
     * 续保（3已过期 → 4已续保）
     *
     * @param id 编号
     */
    void renew(Long id);

}
