package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectReceivableRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DirectReceivableMapper {
    record SourceQuery(Long tenantId, String sourceSystem, String sourceKey) {}
    List<DirectReceivableRow> selectSourceForUpdate(@Param("query") SourceQuery query);
    record ContractQuery(Long tenantId, String contractNo) {}
    List<Long> selectExactContractIds(@Param("query") ContractQuery query);
    int insertReceipt(@Param("row") DirectReceivableRow row);
    int updateReceipt(@Param("row") DirectReceivableRow row);
}
