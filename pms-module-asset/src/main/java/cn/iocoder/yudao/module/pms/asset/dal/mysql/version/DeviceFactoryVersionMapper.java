package cn.iocoder.yudao.module.pms.asset.dal.mysql.version;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionDO;
import org.apache.ibatis.annotations.*;
@Mapper
public interface DeviceFactoryVersionMapper extends BaseMapperX<DeviceFactoryVersionDO> {
    default DeviceFactoryVersionDO selectByTenantAndSn(Long tenantId,String sn) {
        return selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<DeviceFactoryVersionDO>()
                .eq(DeviceFactoryVersionDO::getTenantId,tenantId).eq(DeviceFactoryVersionDO::getDeviceSn,sn));
    }
    record IdentityQuery(Long tenantId, String deviceSn) {}
    DeviceFactoryVersionDO selectIdentity(@Param("query") IdentityQuery query);
}
