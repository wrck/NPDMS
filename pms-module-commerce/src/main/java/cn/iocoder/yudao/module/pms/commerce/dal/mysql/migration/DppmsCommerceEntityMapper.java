package cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DppmsCommerceEntityMapper {
    record ShipmentLineLockQuery(Long tenantId,Long id) {}
    cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO selectShipmentLineForUpdate(
            @Param("query") ShipmentLineLockQuery query);
    record ContractQuery(Long tenantId, Long projectId, String contractNo, LocalDateTime at) {}
    record SourceQuery(Long tenantId, String sourceKey) {}
    record ExecutionQuery(Long tenantId, Long projectId, String sourceSystem, String executionNo) {}
    @lombok.Builder
    record AssociatedOrderQuery(Long tenantId,Long projectId,String contractNo,String source,String companyCode,
                                String rawCompanyCode,String type,String number,LocalDateTime at) {}
    record AssociatedLineQuery(Long tenantId,Long orderId,String lineNo) {}
    List<Long> selectAssociatedOrderIds(@Param("query") AssociatedOrderQuery query);
    List<Long> selectAssociatedLineIds(@Param("query") AssociatedLineQuery query);
    record PackageQuery(Long tenantId, String packageNo) {}
    List<Long> selectContractIds(@Param("query") ContractQuery query);
    List<Long> selectOrderIds(@Param("query") SourceQuery query);
    List<Long> selectLineIds(@Param("query") SourceQuery query);
    List<Long> selectExecutionIds(@Param("query") ExecutionQuery query);
    List<Long> selectPackageIds(@Param("query") PackageQuery query);
}
