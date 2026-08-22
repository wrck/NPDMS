package cn.iocoder.yudao.module.pms.asset.dal.mysql.location;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.LocationSourceMappingDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LocationSourceMappingMapper extends BaseMapperX<LocationSourceMappingDO> {

    default LocationSourceMappingDO selectBySourceKey(String sourceSystem, String objectType, String sourceKey) {
        return selectOne(LocationSourceMappingDO::getSourceSystem, sourceSystem,
                LocationSourceMappingDO::getObjectType, objectType,
                LocationSourceMappingDO::getSourceKey, sourceKey);
    }

    default int updateByIdAndVersion(LocationSourceMappingDO update, Integer expectedVersion) {
        return update(update, new LambdaUpdateWrapper<LocationSourceMappingDO>()
                .eq(LocationSourceMappingDO::getId, update.getId())
                .eq(LocationSourceMappingDO::getVersion, expectedVersion));
    }
}
