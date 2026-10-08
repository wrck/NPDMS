package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** A concrete business Mapper only binds E; complex business queries remain in its own XML. */
public interface BusinessMapper<E extends BaseProjectBusinessEntity> extends BaseMapper<E> {
    record RuntimeCandidates(Long tenantId,Long projectId,Long afterId,int limit) { }
    default java.util.List<E> selectRuntimeCandidates(RuntimeCandidates query) {
        if(query.tenantId()==null || query.projectId()==null || query.projectId()<=0 || query.limit()<1 || query.limit()>200 || query.afterId()!=null && query.afterId()<=0)
            throw new IllegalArgumentException("BUSINESS_RUNTIME_QUERY_INVALID");
        Class<E> entityType=(Class<E>)org.springframework.core.ResolvableType.forInstance(this).as(BaseMapper.class).getGeneric(0).resolve();
        if(entityType==null)throw new IllegalArgumentException("BUSINESS_MAPPER_TYPE_UNRESOLVED");
        var conditions=new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<E>();
        conditions.setEntityClass(entityType);
        conditions.eq(BaseProjectBusinessEntity::getTenantId,query.tenantId()).eq(BaseProjectBusinessEntity::getProjectId,query.projectId())
                .gt(query.afterId()!=null,BaseProjectBusinessEntity::getId,query.afterId()).orderByAsc(BaseProjectBusinessEntity::getId);
        return selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<E>(1,query.limit(),false),conditions).getRecords();
    }
    default PageResult<E> selectBusinessPage(BusinessReadQuery query) {
        return BusinessMapperQueries.page(this, query);
    }
}
