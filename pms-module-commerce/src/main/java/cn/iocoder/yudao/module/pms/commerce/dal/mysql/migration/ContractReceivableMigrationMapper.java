package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.ContractReceivableDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContractReceivableMigrationMapper {
    record SourceQuery(Long tenantId, String sourceSystem, String sourceKey) {}
    List<ContractReceivableDO> selectSourceForUpdate(@Param("query") SourceQuery query);
    record ContractQuery(Long tenantId, String contractNo) {}
    List<Long> selectExactContractIds(@Param("query") ContractQuery query);
    int insertReceipt(@Param("row") ContractReceivableDO row);
    int updateReceipt(@Param("row") ContractReceivableDO row);
}
