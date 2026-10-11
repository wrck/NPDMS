package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.query.SyncBindingPageQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface SyncBindingMapper extends BaseMapperX<SyncBindingDO> {
    default boolean hasTaskBindings(SyncQueries.Task query) {
        return selectCount(new LambdaQueryWrapperX<SyncBindingDO>().eq(SyncBindingDO::getTenantId,query.tenantId())
            .eq(SyncBindingDO::getTaskId,query.taskId())) > 0;
    }
    default List<SyncBindingDO> selectSourceKeys(SyncQueries.SourceKeys query) {
        if(query.sourceKeys()==null || query.sourceKeys().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<SyncBindingDO>()
            .eq(SyncBindingDO::getTenantId,query.tenantId()).eq(SyncBindingDO::getTaskId,query.taskId())
            .eq(SyncBindingDO::getSourceObject,query.sourceObject())
            .eq(SyncBindingDO::getObjectKey,query.object()).in(SyncBindingDO::getSourceKey,query.sourceKeys()));
    }
    int deleteAdapterBindings(SyncQueries.AdapterScope query);
    int deleteTaskBindings(SyncQueries.Task query);
    int updateLastRun(SyncQueries.BindingRun query);
    List<SyncBindingDO> selectCompactionForUpdate(@org.apache.ibatis.annotations.Param("query") SyncQueries.BindingCompaction query);
    default SyncBindingDO selectScoped(SyncQueries.Id query) {
        return selectOne(new LambdaQueryWrapperX<SyncBindingDO>().eq(SyncBindingDO::getTenantId,query.tenantId())
            .eq(SyncBindingDO::getId,query.id()));
    }
    default List<SyncBindingDO> selectTask(SyncQueries.Task query) {
        return selectList(new LambdaQueryWrapperX<SyncBindingDO>().eq(SyncBindingDO::getTenantId,query.tenantId())
            .eq(SyncBindingDO::getTaskId,query.taskId()).orderByAsc(SyncBindingDO::getId));
    }
    List<SyncBindingDO> selectListTaskPage(@org.apache.ibatis.annotations.Param("query") SyncBindingPageQuery query);
    long selectCountTaskPage(@org.apache.ibatis.annotations.Param("query") SyncBindingPageQuery query);
    default PageResult<SyncBindingDO> selectPage(SyncBindingPageQuery query) {
        long total=selectCountTaskPage(query);
        return new PageResult<>(query.offset()>=total?List.of():selectListTaskPage(query),total);
    }
}
