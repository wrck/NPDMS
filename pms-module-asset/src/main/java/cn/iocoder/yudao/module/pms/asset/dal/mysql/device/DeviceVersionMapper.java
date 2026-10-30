package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeviceVersionMapper extends BaseMapperX<DeviceVersionDO> {

    default Integer selectMaxVersionNo(Long deviceId) {
        List<DeviceVersionDO> list = selectList(new LambdaQueryWrapperX<DeviceVersionDO>()
                .eq(DeviceVersionDO::getDeviceId, deviceId)
                .orderByDesc(DeviceVersionDO::getVersionNo));
        return list.isEmpty() ? 0 : list.get(0).getVersionNo();
    }

    default List<DeviceVersionDO> selectListByDeviceId(Long deviceId) {
        return selectList(new LambdaQueryWrapperX<DeviceVersionDO>()
                .eq(DeviceVersionDO::getDeviceId, deviceId)
                .orderByDesc(DeviceVersionDO::getVersionNo));
    }
}
