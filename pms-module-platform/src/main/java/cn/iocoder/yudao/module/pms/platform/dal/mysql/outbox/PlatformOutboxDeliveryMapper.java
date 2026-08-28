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

    int markDeliveredIfPending(@Param("query") OutboxDeliveryUpdateQuery query);

    int scheduleRetryIfPending(@Param("query") OutboxRetryUpdateQuery query);
}
