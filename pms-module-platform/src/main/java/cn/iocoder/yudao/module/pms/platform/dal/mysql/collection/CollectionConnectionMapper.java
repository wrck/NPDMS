package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionConnectionQuery;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface CollectionConnectionMapper {
    List<CredentialGrantDO> effectiveGrants(@Param("q") cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.EffectiveCredentialGrantQuery query);
    List<DeviceCredentialDO> owned(@Param("q") CollectionConnectionQuery query);
    List<DeviceCredentialDO> usable(@Param("q") CollectionConnectionQuery query);
    List<CredentialGrantDO> grants(@Param("q") cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.ConnectionGrantsQuery query);
    DeviceCredentialDO lock(@Param("tenantId") Long tenantId,@Param("id") Long credentialId);
    DeviceCredentialDO byExternal(@Param("tenantId") Long tenantId,@Param("id") String externalId);
}
