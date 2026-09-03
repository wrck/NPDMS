package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.DeliveryScopeProjectVersionDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionAdvance;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.DeliveryScopeProjectVersionSeed;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliveryScopeProjectVersionMapper extends BaseMapperX<DeliveryScopeProjectVersionDO> {
    int insertIfAbsent(DeliveryScopeProjectVersionSeed seed);

    DeliveryScopeProjectVersionDO selectCurrent(DeliveryScopeProjectVersionQuery query);

    DeliveryScopeProjectVersionDO selectForUpdate(DeliveryScopeProjectVersionQuery query);

    int advance(DeliveryScopeProjectVersionAdvance advance);
}
