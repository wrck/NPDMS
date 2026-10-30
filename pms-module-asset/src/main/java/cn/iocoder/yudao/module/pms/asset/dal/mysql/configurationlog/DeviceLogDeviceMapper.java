package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.*;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper public interface DeviceLogDeviceMapper extends BaseMapperX<DeviceDO> {
    default List<DeviceDO> projectDevices(DeviceLogProjectQuery query) {
        return selectList(new LambdaQueryWrapperX<DeviceDO>().eq(DeviceDO::getTenantId, query.tenantId())
                .eq(DeviceDO::getProjectId, query.projectId()).orderByAsc(DeviceDO::getId));
    }
    default List<DeviceDO> matchSerials(DeviceLogSerialsQuery query) {
        if (query.serialNumbers().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<DeviceDO>().eq(DeviceDO::getTenantId, query.tenantId())
                .eq(DeviceDO::getProjectId, query.projectId()).in(DeviceDO::getSn, query.serialNumbers()).orderByAsc(DeviceDO::getId));
    }
}
