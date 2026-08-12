package cn.iocoder.yudao.module.pms.asset.service.equipment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentPageReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentSaveReqVO;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentStatusChangeReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentVersionDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 设备档案 Service 接口（FR-RES-001 / FR-RES-002）。
 * <p>
 * 序列号全局唯一；设备状态变更通过状态机 {@code EquipmentStatusRules} 校验；
 * 每次创建/修改/状态变更追加一条版本历史记录（追加只读）。
 */
public interface EquipmentService {

    /**
     * 创建设备档案
     */
    Long createEquipment(@Valid EquipmentSaveReqVO createReqVO);

    /**
     * 更新设备档案（已报废不允许修改）
     */
    void updateEquipment(@Valid EquipmentSaveReqVO updateReqVO);

    /**
     * 删除设备档案
     */
    void deleteEquipment(Long id);

    /**
     * 查询设备详情
     */
    EquipmentDO getEquipment(Long id);

    /**
     * 校验设备存在
     */
    EquipmentDO validateEquipmentExists(Long id);

    /**
     * 分页查询设备
     */
    PageResult<EquipmentDO> getEquipmentPage(EquipmentPageReqVO pageReqVO);

    /**
     * 设备状态变更（状态机校验 + 版本历史追加）
     */
    void changeEquipmentStatus(@Valid EquipmentStatusChangeReqVO reqVO);

    /**
     * 查询设备版本历史列表（追加只读）
     */
    List<EquipmentVersionDO> getEquipmentVersionList(Long equipmentId);

}
