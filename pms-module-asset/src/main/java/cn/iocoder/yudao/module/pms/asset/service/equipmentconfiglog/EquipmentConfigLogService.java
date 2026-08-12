package cn.iocoder.yudao.module.pms.asset.service.equipmentconfiglog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo.EquipmentConfigLogPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;

/**
 * PMS 设备配置日志 Service 接口（FR-RES-003）。
 * <p>
 * 配置日志为只读档案，仅提供分页查询能力。
 */
public interface EquipmentConfigLogService {

    /**
     * 分页查询设备配置日志
     */
    PageResult<EquipmentConfigLogDO> getEquipmentConfigLogPage(EquipmentConfigLogPageReqVO pageReqVO);

}
