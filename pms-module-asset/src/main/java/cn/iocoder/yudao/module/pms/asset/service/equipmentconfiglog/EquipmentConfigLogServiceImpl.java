package cn.iocoder.yudao.module.pms.asset.service.equipmentconfiglog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo.EquipmentConfigLogPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog.EquipmentConfigLogMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * PMS 设备配置日志 Service 实现（FR-RES-003）。
 * <p>
 * 配置日志为只读档案，仅提供分页查询能力。
 */
@Service
@Validated
public class EquipmentConfigLogServiceImpl implements EquipmentConfigLogService {

    @Resource
    private EquipmentConfigLogMapper equipmentConfigLogMapper;

    @Override
    public PageResult<EquipmentConfigLogDO> getEquipmentConfigLogPage(EquipmentConfigLogPageReqVO pageReqVO) {
        return equipmentConfigLogMapper.selectPage(pageReqVO);
    }

}
