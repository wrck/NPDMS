package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractFactsQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.projection.ProjectContractFactRow;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ExactContractProjectQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
@Mapper
public interface ProjectCommerceMapper {
    List<String> selectWarrantyShipmentContractNumbers(@Param("query") ProjectCommerceQuery query);
    List<ProjectContractFactRow> selectProjectContractFacts(@Param("query") ProjectContractFactsQuery query);
    List<Long> selectProjectIdsByExactContractNo(@Param("query") ExactContractProjectQuery query);
    List<ContractDO> selectContracts(@Param("query") ProjectCommerceQuery query);
    List<SalesOrderDO> selectOrders(@Param("query") ProjectCommerceQuery query);
    List<CrmExecutionOrderDO> selectExecutionOrders(@Param("query") ProjectCommerceQuery query);
    List<cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.history.ContractReceivableDO> selectReceivables(@Param("query") ProjectCommerceQuery query);
}
