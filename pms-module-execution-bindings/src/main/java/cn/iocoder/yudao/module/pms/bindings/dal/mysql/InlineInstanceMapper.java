package cn.iocoder.yudao.module.pms.bindings.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.InlineInstanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface InlineInstanceMapper extends BaseMapperX<InlineInstanceDO> {

    default Optional<InlineInstanceDO> selectByIdempotencyKey(String idempotencyKey) {
        return selectList(new LambdaQueryWrapperX<InlineInstanceDO>()
                .eq(InlineInstanceDO::getIdempotencyKey, idempotencyKey)
                .orderByDesc(InlineInstanceDO::getId))
                .stream().findFirst();
    }

    default List<InlineInstanceDO> selectByDefinition(String definitionCode) {
        return selectList(new LambdaQueryWrapperX<InlineInstanceDO>()
                .eq(InlineInstanceDO::getDefinitionCode, definitionCode)
                .orderByDesc(InlineInstanceDO::getId));
    }
}
