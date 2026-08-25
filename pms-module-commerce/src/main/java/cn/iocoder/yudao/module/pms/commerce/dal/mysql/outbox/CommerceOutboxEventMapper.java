package cn.iocoder.yudao.module.pms.commerce.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommerceOutboxEventMapper extends BaseMapperX<CommerceOutboxEventDO> {
    default CommerceOutboxEventDO selectByEventId(String eventId) {
        return selectOne(CommerceOutboxEventDO::getEventId, eventId);
    }
}
