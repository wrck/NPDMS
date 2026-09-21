package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionReconciliationDueQuery;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskCallbackUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskConsumptionUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationObservationUpdate;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskReconciliationUpdate;
import cn.iocoder.yudao.module.pms.platform.service.collection.CollectionTaskDispatchUpdate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CollectionTaskMapper extends BaseMapperX<CollectionTaskDO> {

    List<String> RECONCILIATION_TERMINAL_STATUSES = List.of(
            "COMPLETED", "FAILED", "CANCELLED", "SECURITY_EXCEPTION");

    CollectionTaskDO selectByTenantAndPlatformTaskId(@Param("tenantId") Long tenantId,
                                                      @Param("platformTaskId") String platformTaskId);

    List<CollectionTaskDO> selectReconciliationDue(@Param("query") CollectionReconciliationDueQuery query);

    CollectionTaskDO selectByTenantAndPlatformTaskIdForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("platformTaskId") String platformTaskId);

    int updateDispatchState(@Param("update") CollectionTaskDispatchUpdate update);

    int updateUndispatchedCancellation(@Param("update")
            cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTaskUndispatchedCancellationUpdate update);

    int updateCallbackState(@Param("update") CollectionTaskCallbackUpdate update);

    int updateReconciliationState(@Param("update") CollectionTaskReconciliationUpdate update);

    int updateReconciliationObservation(@Param("update") CollectionTaskReconciliationObservationUpdate update);

    int updateConsumptionState(@Param("update") CollectionTaskConsumptionUpdate update);

    List<CollectionTaskDO> selectListByBatchId(Long batchId);
}
