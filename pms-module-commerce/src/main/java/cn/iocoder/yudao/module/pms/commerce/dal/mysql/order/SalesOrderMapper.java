package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.common.query.AuthoritySourceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ContractCreationOrderQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ContractRelatedOrderQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SalesOrderMapper extends BaseMapperX<SalesOrderDO> {
    SalesOrderDO selectBySourceForUpdate(@Param("query") AuthoritySourceLockQuery query);
    List<SalesOrderDO> selectByCompanyScope(@Param("query") SalesOrderCompanyScopeQuery query);
    Long selectCountByCompanyScope(@Param("query") SalesOrderCompanyScopeQuery query);
    SalesOrderDO selectDetailByCompanyScope(@Param("query") cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderDetailScopeQuery query);
    List<SalesOrderDO> selectRootReadPage(@Param("query") cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.SalesOrderRootReadPageQuery query);
    List<SalesOrderDO> selectRelatedByContract(@Param("query") ContractRelatedOrderQuery query);
    List<SalesOrderDO> selectCreationOrdersByContract(@Param("query") ContractCreationOrderQuery query);
    List<SalesOrderDO> selectCreationOrdersForUpdate(@Param("query") ContractCreationOrderQuery query);
    List<cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO> selectCreationRelationsByContract(@Param("query") ContractRelatedOrderQuery query);
    List<cn.iocoder.yudao.module.pms.commerce.dal.dataobject.authority.SalesOrderContractRelationDO> selectCreationRelationsForUpdate(@Param("query") ContractRelatedOrderQuery query);
}
