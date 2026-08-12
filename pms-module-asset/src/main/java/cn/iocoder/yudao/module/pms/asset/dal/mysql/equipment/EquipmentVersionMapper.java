package cn.iocoder.yudao.module.pms.asset.dal.mysql.equipment;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.equipment.EquipmentVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EquipmentVersionMapper extends BaseMapperX<EquipmentVersionDO> {

    default List<EquipmentVersionDO> selectListByEquipmentId(Long equipmentId) {
        return selectList(new LambdaQueryWrapperX<EquipmentVersionDO>()
                .eq(EquipmentVersionDO::getEquipmentId, equipmentId)
                .orderByAsc(EquipmentVersionDO::getVersionNo));
    }

    default Integer selectMaxVersionNo(Long equipmentId) {
        EquipmentVersionDO entity = selectOne(new LambdaQueryWrapperX<EquipmentVersionDO>()
                .eq(EquipmentVersionDO::getEquipmentId, equipmentId)
                .orderByDesc(EquipmentVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return entity == null ? 0 : entity.getVersionNo();
    }

}
