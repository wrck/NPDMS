package cn.iocoder.yudao.module.pms.asset.dal.mysql.device;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface DeviceMigrationIdentityMapper {
    record DeviceIdentityQuery(Long tenantId,String deviceSn) {}
    DeviceDO selectIdentityForUpdate(@Param("query") DeviceIdentityQuery query);
}
