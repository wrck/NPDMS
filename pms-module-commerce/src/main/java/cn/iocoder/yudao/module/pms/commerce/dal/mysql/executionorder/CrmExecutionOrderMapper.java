package cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.executionorder.CrmExecutionOrderDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.CrmExecutionOrderSyncQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionNoListQuery;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.executionorder.query.ExecutionPrimaryProjectUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CrmExecutionOrderMapper extends BaseMapperX<CrmExecutionOrderDO> {
    List<CrmExecutionOrderDO> selectIncoming(@Param("query") CrmExecutionOrderSyncQuery query);
    List<CrmExecutionOrderDO> selectActiveByExecutionNos(@Param("query") ExecutionNoListQuery query);
    int updatePrimaryProjectIfUnbound(@Param("query") ExecutionPrimaryProjectUpdate query);
}
