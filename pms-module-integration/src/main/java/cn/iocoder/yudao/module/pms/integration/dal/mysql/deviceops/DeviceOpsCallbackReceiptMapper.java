package cn.iocoder.yudao.module.pms.integration.dal.mysql.deviceops;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.deviceops.DeviceOpsCallbackReceiptDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceOpsCallbackReceiptMapper extends BaseMapperX<DeviceOpsCallbackReceiptDO> {
    default DeviceOpsCallbackReceiptDO find(Long tenantId, String callbackId) {
        return selectOne(new LambdaQueryWrapperX<DeviceOpsCallbackReceiptDO>()
                .eq(DeviceOpsCallbackReceiptDO::getTenantId, tenantId)
                .eq(DeviceOpsCallbackReceiptDO::getCallbackId, callbackId));
    }
}
