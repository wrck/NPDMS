package cn.iocoder.yudao.module.pms.service.service.srvmaintenance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceOverrideReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenancePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenanceSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvmaintenance.SrvMaintenanceDO;

import java.util.List;

/**
 * 维保状态 Service 接口
 */
public interface SrvMaintenanceService {

    /**
     * 创建维保记录
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createSrvMaintenance(SrvMaintenanceSaveReqVO createReqVO);

    /**
     * 更新维保记录
     *
     * @param updateReqVO 更新信息
     */
    void updateSrvMaintenance(SrvMaintenanceSaveReqVO updateReqVO);

    /**
     * 删除维保记录
     *
     * @param id 编号
     */
    void deleteSrvMaintenance(Long id);

    /**
     * 获得维保记录分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<SrvMaintenanceDO> getSrvMaintenancePage(SrvMaintenancePageReqVO pageReqVO);

    /**
     * 获得维保记录
     *
     * @param id 编号
     * @return 维保记录
     */
    SrvMaintenanceDO getSrvMaintenance(Long id);

    /**
     * 根据设备编号获得维保记录列表
     *
     * @param equipmentId 设备编号
     * @return 维保记录列表
     */
    List<SrvMaintenanceDO> getSrvMaintenanceListByEquipment(Long equipmentId);

    /**
     * 自动计算维保状态
     *
     * @param id 编号
     */
    void calculateStatus(Long id);

    /**
     * 手工覆盖维保状态
     *
     * @param reqVO 覆盖信息
     */
    void manualOverride(SrvMaintenanceOverrideReqVO reqVO);

}
