package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.*;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.*;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface DppmsCommerceDetailMapper {
    record ExecutionIdentityQuery(Long tenantId,Long projectId,Long id,String sourceSystem,String executionNo) {}
    record ExecutionEnrichmentUpdate(Long tenantId,Long projectId,Long version,Long actorId,
                                     java.time.LocalDateTime correctedAt,CrmExecutionOrderDO row) {}
    CrmExecutionOrderDO selectExecutionForUpdate(@Param("query") ExecutionIdentityQuery query);
    int updateExecutionFields(@Param("command") ExecutionEnrichmentUpdate command);
    record IdQuery(Long tenantId,Long id) {}
    record PackageQuery(Long tenantId,Long projectId,String contractNo,String packageNo) {}
    record PageQuery(Long tenantId,Long projectId,int offset,int limit) {}
    SalesOrderDO selectOrderForUpdate(@Param("query") IdQuery query);
    SalesOrderLineDO selectLineForUpdate(@Param("query") IdQuery query);
    List<ShipmentPackageDO> selectPackageForUpdate(@Param("query") PackageQuery query);
    record PackageContractIdentity(String sourceSystem,String sourceRecordKey) {}
    PackageContractIdentity selectPackageContractIdentity(@Param("query") IdQuery query);
    List<CrmExecutionOrderDO> selectExecutions(@Param("query") PageQuery query);
    Long countExecutions(@Param("query") PageQuery query);
    List<SalesOrderDO> selectOrders(@Param("query") PageQuery query);
    Long countOrders(@Param("query") PageQuery query);
    List<SalesOrderLineDO> selectLines(@Param("query") PageQuery query);
    Long countLines(@Param("query") PageQuery query);
    List<ContractCollectionPlanDO> selectPlans(@Param("query") PageQuery query);
    Long countPlans(@Param("query") PageQuery query);
    List<ShipmentPackageDO> selectPackages(@Param("query") PageQuery query);
    Long countPackages(@Param("query") PageQuery query);
}
