package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ContractProjectScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query.ContractScopeOrderLineRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContractProjectScopeMapper {
    List<ContractDO> selectContractsForUpdate(@Param("query") ContractProjectScopeQuery query);
    List<ContractScopeOrderLineRow> selectOrderLinesForUpdate(@Param("query") ContractProjectScopeQuery query);
}
