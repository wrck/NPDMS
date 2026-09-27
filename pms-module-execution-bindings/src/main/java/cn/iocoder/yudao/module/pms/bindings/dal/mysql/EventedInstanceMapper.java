package cn.iocoder.yudao.module.pms.bindings.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.EventedInstanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface EventedInstanceMapper extends BaseMapperX<EventedInstanceDO> {

    default Optional<EventedInstanceDO> selectByEventAndDefinition(String eventId, String definitionCode) {
        return selectList(new LambdaQueryWrapperX<EventedInstanceDO>()
                .eq(EventedInstanceDO::getEventId, eventId)
                .eq(EventedInstanceDO::getDefinitionCode, definitionCode)
                .orderByDesc(EventedInstanceDO::getId))
                .stream().findFirst();
    }

    default List<EventedInstanceDO> selectByDefinition(String definitionCode) {
        return selectList(new LambdaQueryWrapperX<EventedInstanceDO>()
                .eq(EventedInstanceDO::getDefinitionCode, definitionCode)
                .orderByDesc(EventedInstanceDO::getId));
    }
}
