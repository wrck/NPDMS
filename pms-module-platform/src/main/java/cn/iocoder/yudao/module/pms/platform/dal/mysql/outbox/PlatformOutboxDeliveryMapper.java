package cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.DueOutboxListQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.OutboxDeliveryUpdateQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query.OutboxRetryUpdateQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PlatformOutboxDeliveryMapper extends BaseMapperX<PlatformOutboxEventDO> {

    List<PlatformOutboxEventDO> selectDueForUpdate(@Param("query") DueOutboxListQuery query);

    /** 统一业务事件受控读取：按事件类型读待处理事件，跨租户、只读、不改事件状态；afterId 为消费游标。 */
    List<PlatformOutboxEventDO> selectPendingByEventType(@Param("eventType") String eventType,
                                                         @Param("limit") int limit,
                                                         @Param("afterId") long afterId);

    int markDeliveredIfPending(@Param("query") OutboxDeliveryUpdateQuery query);

    int scheduleRetryIfPending(@Param("query") OutboxRetryUpdateQuery query);
}
