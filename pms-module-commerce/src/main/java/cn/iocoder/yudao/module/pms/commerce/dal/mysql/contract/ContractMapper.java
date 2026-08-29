package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.common.query.AuthoritySourceLockQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractCompanyScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractDetailScopeQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ContractIdLockQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContractMapper extends BaseMapperX<ContractDO> {
    ContractDO selectBySourceForUpdate(@Param("query") AuthoritySourceLockQuery query);
    List<ContractDO> selectByCompanyScope(@Param("query") ContractCompanyScopeQuery query);
    Long selectCountByCompanyScope(@Param("query") ContractCompanyScopeQuery query);
    ContractDO selectDetailByCompanyScope(@Param("query") ContractDetailScopeQuery query);
    ContractDO selectByIdForUpdate(@Param("query") ContractIdLockQuery query);
}
