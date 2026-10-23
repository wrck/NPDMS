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
    List<SyncBindingDO> selectRetiredSourceKeysForUpdate(@org.apache.ibatis.annotations.Param("query") SyncQueries.SourceKeys query);
    List<SyncBindingDO> selectInstallationSelectionPage(@org.apache.ibatis.annotations.Param("query") SyncQueries.InstallationSelection query);
    int restoreVerifiedBinding(@org.apache.ibatis.annotations.Param("original") SyncBindingDO original);
    List<SyncBindingDO> selectObsoleteOrderLinesForUpdate(@org.apache.ibatis.annotations.Param("query") SyncQueries.ObsoleteOrderLines query);
    int deleteObsoleteOrderLines(@org.apache.ibatis.annotations.Param("query") SyncQueries.ObsoleteOrderLineDelete query);
    default List<SyncBindingDO> selectDeliveryProjectionPage(SyncQueries.DeliveryProjectionBindings query) {
        return selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<SyncBindingDO>(1,500,false),
                new LambdaQueryWrapperX<SyncBindingDO>().eq(SyncBindingDO::getTenantId,query.tenantId())
                        .eq(SyncBindingDO::getTaskId,query.taskId()).eq(SyncBindingDO::getLastRunId,query.runId())
                        .eq(SyncBindingDO::getObjectKey,query.object()).eq(SyncBindingDO::getSourceObject,query.sourceObject())
                        .gtIfPresent(SyncBindingDO::getId,query.afterId()).orderByAsc(SyncBindingDO::getId)
                        .select(SyncBindingDO::getId,SyncBindingDO::getTargetId,SyncBindingDO::getFieldsJson)).getRecords();
    }
    default List<SyncBindingDO> selectDeliveryInputs(SyncQueries.DeliveryBindings query) {
        return selectList(new LambdaQueryWrapperX<SyncBindingDO>()
                .eq(SyncBindingDO::getTenantId,query.tenantId()).eq(SyncBindingDO::getTaskId,query.taskId())
                .eq(SyncBindingDO::getObjectKey,query.object()).eq(SyncBindingDO::getSourceObject,query.sourceObject())
                .eqIfPresent(SyncBindingDO::getLastRunId,query.runId()).select(SyncBindingDO::getTargetId));
    }
    default boolean hasTaskBindings(SyncQueries.Task query) {
        return selectFirstTaskBindingId(query) != null;
    }
    Long selectFirstTaskBindingId(SyncQueries.Task query);
    default List<SyncBindingDO> selectSourceKeys(SyncQueries.SourceKeys query) {
        if(query.sourceKeys()==null || query.sourceKeys().isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapperX<SyncBindingDO>()
            .eq(SyncBindingDO::getTenantId,query.tenantId()).eq(SyncBindingDO::getTaskId,query.taskId())
            .eq(SyncBindingDO::getSourceObject,query.sourceObject())
            .eq(SyncBindingDO::getObjectKey,query.object()).in(SyncBindingDO::getSourceKey,query.sourceKeys()));
    }
    int deleteAdapterBindings(SyncQueries.AdapterScope query);
    List<SyncBindingDO> selectIdentitySources(@org.apache.ibatis.annotations.Param("query") SyncQueries.IdentitySources query);
    List<SyncBindingDO> selectEhrEmployeeUsers(@org.apache.ibatis.annotations.Param("query") SyncQueries.EhrEmployeeUsers query);
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
