package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.common.query.AuthoritySourceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderLineIdsQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SalesOrderLineMapper extends BaseMapperX<SalesOrderLineDO> {
    SalesOrderLineDO selectBySourceForUpdate(@Param("query") AuthoritySourceLockQuery query);
    List<SalesOrderLineDO> selectByCompanyScope(@Param("query") SalesOrderLineCompanyScopeQuery query);
    Long selectCountByCompanyScope(@Param("query") SalesOrderLineCompanyScopeQuery query);
    List<SalesOrderLineDO> selectByIds(@Param("query") SalesOrderLineIdsQuery query);
    List<SalesOrderLineDO> selectByIdsForUpdate(@Param("query") SalesOrderLineIdsQuery query);
}
