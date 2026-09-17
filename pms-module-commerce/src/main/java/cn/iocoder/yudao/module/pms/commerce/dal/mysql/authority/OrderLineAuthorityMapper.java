package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceKeysQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.AuthoritySourceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query.OrderLineAuthorityUpdate;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderLineAuthorityMapper extends BaseMapperX<SalesOrderLineDO> {
    SalesOrderLineDO selectBySourceForUpdate(AuthoritySourceQuery query);

    List<SalesOrderLineDO> selectBySourcesForUpdate(AuthoritySourceKeysQuery query);

    int updateOwnerByVersion(OrderLineAuthorityUpdate update);
}
