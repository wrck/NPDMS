package cn.iocoder.yudao.module.pms.asset.dal.mysql.equipmentconfiglog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.controller.admin.equipmentconfiglog.vo.EquipmentConfigLogPageReqVO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipmentconfiglog.EquipmentConfigLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EquipmentConfigLogMapper extends BaseMapperX<EquipmentConfigLogDO> {

    default PageResult<EquipmentConfigLogDO> selectPage(EquipmentConfigLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EquipmentConfigLogDO>()
                .eqIfPresent(EquipmentConfigLogDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(EquipmentConfigLogDO::getConfigType, reqVO.getConfigType())
                .likeIfPresent(EquipmentConfigLogDO::getSourceSystem, reqVO.getSourceSystem())
                .eqIfPresent(EquipmentConfigLogDO::getFileHash, reqVO.getFileHash())
                .betweenIfPresent(EquipmentConfigLogDO::getCollectedAt, reqVO.getCollectedAt())
                .orderByDesc(EquipmentConfigLogDO::getCollectedAt));
    }

}
