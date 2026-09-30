package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Collection;
import java.util.List;

@Mapper
public interface DeviceOrganizationMapper {
    List<DeviceDO> selectBatchForUpdate(@Param("query") DeviceOrganizationBatchQuery query);
    List<DeviceDO> selectStoredOrganizations(@Param("query") DeviceOrganizationBatchQuery query);
    int updateOrganizationBatch(@Param("query") DeviceOrganizationUpdate query, @Param("deviceIds") Collection<Long> deviceIds);
}
