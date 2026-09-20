package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectCommerceQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ContractDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
@Mapper
public interface ProjectCommerceMapper {
    List<ContractDO> selectContracts(@Param("query") ProjectCommerceQuery query);
    List<SalesOrderDO> selectOrders(@Param("query") ProjectCommerceQuery query);
    List<CrmExecutionOrderDO> selectExecutionOrders(@Param("query") ProjectCommerceQuery query);
}
