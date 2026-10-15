package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ShippedProjectQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ProjectDeviceShipmentMapper {
    List<Long> selectShippedProjectIds(@Param("query") ShippedProjectQuery query);
    List<cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO> selectProjectDevices(
            @Param("query") cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery query);
    long countProjectDevices(
            @Param("query") cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectShippedDevicePageQuery query);
}
