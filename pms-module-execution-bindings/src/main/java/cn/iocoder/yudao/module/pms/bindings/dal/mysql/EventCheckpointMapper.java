package cn.iocoder.yudao.module.pms.bindings.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.EventCheckpointDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface EventCheckpointMapper extends BaseMapperX<EventCheckpointDO> {

    default Optional<EventCheckpointDO> selectByEventId(String eventId) {
        return selectList(new LambdaQueryWrapperX<EventCheckpointDO>()
                .eq(EventCheckpointDO::getEventId, eventId)
                .orderByDesc(EventCheckpointDO::getId))
                .stream().findFirst();
    }
}
