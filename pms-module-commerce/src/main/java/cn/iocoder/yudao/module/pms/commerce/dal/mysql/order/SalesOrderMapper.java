package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.common.query.AuthoritySourceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderCompanyScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SalesOrderMapper extends BaseMapperX<SalesOrderDO> {
    SalesOrderDO selectBySourceForUpdate(@Param("query") AuthoritySourceLockQuery query);
    List<SalesOrderDO> selectByCompanyScope(@Param("query") SalesOrderCompanyScopeQuery query);
    Long selectCountByCompanyScope(@Param("query") SalesOrderCompanyScopeQuery query);
}
