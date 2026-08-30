package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeDetailDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityProjectVersionQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityScopeDetailsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityScopeImpactQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthorityScopeReleaseUpdate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AuthorityScopeImpactMapper extends BaseMapperX<DeliveryScopeDO> {
    List<DeliveryScopeDO> selectActiveScopesForUpdate(AuthorityScopeImpactQuery query);

    List<DeliveryScopeDetailDO> selectDetailsForUpdate(AuthorityScopeDetailsQuery query);

    DeliveryScopeProjectVersionDO selectProjectVersionForUpdate(AuthorityProjectVersionQuery query);

    int insertScopeDetail(DeliveryScopeDetailDO row);

    int releaseActiveScopeByVersion(AuthorityScopeReleaseUpdate update);

    int insertProjectVersion(DeliveryScopeProjectVersionDO row);

    int updateProjectVersionById(DeliveryScopeProjectVersionDO row);
}
