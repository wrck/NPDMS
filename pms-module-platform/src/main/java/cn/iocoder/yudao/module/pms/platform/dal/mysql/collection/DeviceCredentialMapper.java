package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.DeviceCredentialDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeviceCredentialMapper extends BaseMapperX<DeviceCredentialDO> {

    DeviceCredentialDO selectByTenantAndId(@Param("tenantId") Long tenantId,
                                            @Param("credentialId") Long credentialId);
}
