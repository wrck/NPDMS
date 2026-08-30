package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope.OrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.OrderLineAuthorityUpdate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderLineAuthorityMapper extends BaseMapperX<OrderLineDO> {
    OrderLineDO selectBySourceForUpdate(AuthoritySourceQuery query);

    int updateOwnerByVersion(OrderLineAuthorityUpdate update);
}
