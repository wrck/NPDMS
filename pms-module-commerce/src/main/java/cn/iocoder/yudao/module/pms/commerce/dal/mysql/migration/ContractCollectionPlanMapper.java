package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.ContractCollectionPlanDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContractCollectionPlanMapper {
    record IdentityQuery(Long tenantId, Long id) {}
    record ContractQuery(Long tenantId, String contractNo) {}
    ContractCollectionPlanDO selectIdentity(@Param("query") IdentityQuery query);
    ContractCollectionPlanDO selectIdentityForUpdate(@Param("query") IdentityQuery query);
    List<Long> selectExactContractIds(@Param("query") ContractQuery query);
    int insertPlan(@Param("row") ContractCollectionPlanDO row);
    int updatePlan(@Param("row") ContractCollectionPlanDO row);
}
