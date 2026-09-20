package cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.CrmExecutionOrderSyncQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CrmExecutionOrderMapper extends BaseMapperX<CrmExecutionOrderDO> {
    List<CrmExecutionOrderDO> selectIncoming(@Param("query") CrmExecutionOrderSyncQuery query);
}
