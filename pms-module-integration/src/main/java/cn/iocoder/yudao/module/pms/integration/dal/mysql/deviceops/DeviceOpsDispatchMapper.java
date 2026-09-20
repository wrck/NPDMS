package cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsDispatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceOpsDispatchMapper extends BaseMapperX<DeviceOpsDispatchDO> {

    default DeviceOpsDispatchDO selectByPlatformTaskId(String platformTaskId) {
        return selectOne(new LambdaQueryWrapperX<DeviceOpsDispatchDO>()
                .eq(DeviceOpsDispatchDO::getPlatformTaskId, platformTaskId));
    }

    default DeviceOpsDispatchDO selectByTenantPlatformAndCollection(Long tenantId, String platformTaskId,
                                                                    String collectionId) {
        return selectOne(new LambdaQueryWrapperX<DeviceOpsDispatchDO>()
                .eq(DeviceOpsDispatchDO::getTenantId, tenantId)
                .eq(DeviceOpsDispatchDO::getPlatformTaskId, platformTaskId)
                .eq(DeviceOpsDispatchDO::getCollectionId, collectionId));
    }
}
