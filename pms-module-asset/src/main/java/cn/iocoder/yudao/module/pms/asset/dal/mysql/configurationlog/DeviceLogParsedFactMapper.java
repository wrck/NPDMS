package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParsedFactDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceParsedLogHistoryQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DeviceLogParsedFactMapper extends BaseMapperX<DeviceLogParsedFactDO> {
    default DeviceLogParsedFactDO byRequest(Long tenantId, String requestKey) {
        return selectOne(new LambdaQueryWrapperX<DeviceLogParsedFactDO>()
                .eq(DeviceLogParsedFactDO::getTenantId,tenantId).eq(DeviceLogParsedFactDO::getRequestKey,requestKey));
    }
    default List<DeviceLogParsedFactDO> history(DeviceParsedLogHistoryQuery query) {
        return selectList(new LambdaQueryWrapperX<DeviceLogParsedFactDO>()
                .eq(DeviceLogParsedFactDO::getTenantId,query.tenantId()).eq(DeviceLogParsedFactDO::getDeviceId,query.deviceId())
                .orderByDesc(DeviceLogParsedFactDO::getLogReceivedAt).orderByDesc(DeviceLogParsedFactDO::getId));
    }
}
