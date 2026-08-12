package cn.iocoder.yudao.module.pms.service.dal.mysql.srvmaintenance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvmaintenance.vo.SrvMaintenancePageReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvmaintenance.SrvMaintenanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SrvMaintenanceMapper extends BaseMapperX<SrvMaintenanceDO> {

    default SrvMaintenanceDO selectByEquipmentIdAndCode(Long equipmentId, String code) {
        return selectOne(new LambdaQueryWrapperX<SrvMaintenanceDO>()
                .eq(SrvMaintenanceDO::getEquipmentId, equipmentId)
                .eq(SrvMaintenanceDO::getCode, code));
    }

    default List<SrvMaintenanceDO> selectListByEquipmentId(Long equipmentId) {
        return selectList(new LambdaQueryWrapperX<SrvMaintenanceDO>()
                .eq(SrvMaintenanceDO::getEquipmentId, equipmentId)
                .orderByDesc(SrvMaintenanceDO::getId));
    }

    default PageResult<SrvMaintenanceDO> selectPage(SrvMaintenancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SrvMaintenanceDO>()
                .eqIfPresent(SrvMaintenanceDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(SrvMaintenanceDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(SrvMaintenanceDO::getCode, reqVO.getCode())
                .eqIfPresent(SrvMaintenanceDO::getMaintenanceStatus, reqVO.getMaintenanceStatus())
                .eqIfPresent(SrvMaintenanceDO::getServiceLevel, reqVO.getServiceLevel())
                .eqIfPresent(SrvMaintenanceDO::getAutoCalculated, reqVO.getAutoCalculated())
                .eqIfPresent(SrvMaintenanceDO::getManualOverride, reqVO.getManualOverride())
                .orderByDesc(SrvMaintenanceDO::getId));
    }

}
