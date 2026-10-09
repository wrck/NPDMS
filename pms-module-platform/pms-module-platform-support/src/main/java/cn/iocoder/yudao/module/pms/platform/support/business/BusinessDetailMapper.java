package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.support.entity.OwnedBusinessDetailEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.core.ResolvableType;
import java.util.List;

/** No child Controller or independent write service is exposed. */
public interface BusinessDetailMapper<D extends OwnedBusinessDetailEntity> extends BaseMapper<D> {
    record OwnedRows(Long tenantId,String aggregateType,Long aggregateId,Long revisionId) { }
    record Removal(OwnedRows scope,Long id,long version,String updater) { }
    @SuppressWarnings("unchecked")
    default int softDeleteOwned(Removal command){
        var scope=command.scope();
        Class<D> type=(Class<D>)ResolvableType.forInstance(this).as(BaseMapper.class).getGeneric(0).resolve();
        var update=new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<D>();update.setEntityClass(type);
        update.eq(OwnedBusinessDetailEntity::getTenantId,scope.tenantId()).eq(OwnedBusinessDetailEntity::getAggregateType,scope.aggregateType())
                .eq(OwnedBusinessDetailEntity::getAggregateId,scope.aggregateId()).eq(OwnedBusinessDetailEntity::getAggregateRevisionId,scope.revisionId())
                .eq(OwnedBusinessDetailEntity::getId,command.id()).eq(OwnedBusinessDetailEntity::getVersion,command.version())
                .set(OwnedBusinessDetailEntity::getDeleted,true).set(OwnedBusinessDetailEntity::getVersion,Math.incrementExact(command.version()))
                .set(OwnedBusinessDetailEntity::getUpdater,command.updater()).set(OwnedBusinessDetailEntity::getUpdateTime,java.time.LocalDateTime.now());
        return update(null,update);
    }

    @SuppressWarnings("unchecked")
    default List<D> selectOwnedRows(OwnedRows query) {
        if(query.tenantId()==null || query.aggregateType()==null || query.aggregateId()==null
                || query.aggregateId()<=0 || query.revisionId()==null || query.revisionId()<0)
            throw new IllegalArgumentException("DETAIL_SCOPE_REQUIRED");
        Class<D> type=(Class<D>)ResolvableType.forInstance(this).as(BaseMapper.class).getGeneric(0).resolve();
        var conditions=new LambdaQueryWrapperX<D>();conditions.setEntityClass(type);
        conditions.eq(OwnedBusinessDetailEntity::getTenantId,query.tenantId())
                .eq(OwnedBusinessDetailEntity::getAggregateType,query.aggregateType())
                .eq(OwnedBusinessDetailEntity::getAggregateId,query.aggregateId())
                .eq(OwnedBusinessDetailEntity::getAggregateRevisionId,query.revisionId())
                .orderByAsc(OwnedBusinessDetailEntity::getDetailOrder,OwnedBusinessDetailEntity::getId);
        return selectList(conditions);
    }
}
