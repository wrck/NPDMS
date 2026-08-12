package cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipment.vo.EquipmentPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentMapper extends BaseMapperX<EquipmentDO> {

    default PageResult<EquipmentDO> selectPage(EquipmentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EquipmentDO>()
                .eqIfPresent(EquipmentDO::getSerialNumber, reqVO.getSerialNumber())
                .likeIfPresent(EquipmentDO::getName, reqVO.getName())
                .eqIfPresent(EquipmentDO::getModel, reqVO.getModel())
                .eqIfPresent(EquipmentDO::getCustomerId, reqVO.getCustomerId())
                .eqIfPresent(EquipmentDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(EquipmentDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(EquipmentDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(EquipmentDO::getId));
    }

    default EquipmentDO selectBySerialNumber(String serialNumber) {
        return selectOne(EquipmentDO::getSerialNumber, serialNumber);
    }

}
