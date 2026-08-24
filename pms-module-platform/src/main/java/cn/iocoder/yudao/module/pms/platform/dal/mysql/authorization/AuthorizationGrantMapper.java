package cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.authorization.AuthorizationGrantDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.AuthorizationGrantKeyQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.AuthorizationGrantPageCriteria;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.AuthorizationGrantRevokeUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query.EffectiveAuthorizationGrantQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuthorizationGrantMapper extends BaseMapperX<AuthorizationGrantDO> {

    AuthorizationGrantDO selectByTenantAndId(@Param("tenantId") Long tenantId,
                                              @Param("grantId") Long grantId);

    List<AuthorizationGrantDO> selectListEffective(@Param("query") EffectiveAuthorizationGrantQuery query);

    List<AuthorizationGrantDO> selectListPage(@Param("query") AuthorizationGrantPageCriteria query);

    long selectCountPage(@Param("query") AuthorizationGrantPageCriteria query);

    int expireCurrentByKey(@Param("query") AuthorizationGrantKeyQuery query);

    int revoke(@Param("update") AuthorizationGrantRevokeUpdate update);
}
