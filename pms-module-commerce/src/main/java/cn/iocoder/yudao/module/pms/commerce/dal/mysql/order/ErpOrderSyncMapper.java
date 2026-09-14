package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.order.SalesOrderLineDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query.ErpOrderSyncQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ErpOrderSyncMapper {
    List<SalesOrderDO> selectOrders(@Param("query") ErpOrderSyncQuery query);
    List<SalesOrderLineDO> selectLines(@Param("query") ErpOrderSyncQuery query);
}
