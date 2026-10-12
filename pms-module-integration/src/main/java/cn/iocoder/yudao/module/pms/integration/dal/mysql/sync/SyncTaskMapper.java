package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.query.SyncTaskPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Comparator;
import java.util.List;
@Mapper
public interface SyncTaskMapper extends BaseMapperX<SyncTaskDO> {
    default SyncTaskDO selectIdentity(SyncQueries.TaskIdentity query) {
        return selectOne(new LambdaQueryWrapperX<SyncTaskDO>().eq(SyncTaskDO::getTenantId,query.tenantId())
                .eq(SyncTaskDO::getSourceSystem,query.sourceSystem()).eq(SyncTaskDO::getAdapter,query.adapter()));
    }
    default SyncTaskDO selectScoped(SyncQueries.Id query) {
        return selectOne(new LambdaQueryWrapperX<SyncTaskDO>().eq(SyncTaskDO::getTenantId,query.tenantId())
            .eq(SyncTaskDO::getId,query.id()));
    }
    SyncTaskDO selectForUpdate(SyncQueries.Id query);
    List<SyncTaskDO> selectAdapterForUpdate(SyncQueries.AdapterScope query);
    List<SyncTaskDO> selectDue(SyncQueries.Due query);
    List<SyncTaskDO> selectPageRows(@Param("query") SyncTaskPageQuery query);
    default PageResult<SyncTaskDO> selectPage(SyncQueries.Page query) {
        var page = SyncTaskPageQuery.from(query);
        long total = selectCount(new LambdaQueryWrapperX<SyncTaskDO>()
                .eq(SyncTaskDO::getTenantId, page.tenantId()));
        if (page.offset() >= total) return PageResult.empty(total);
        // Sort only IDs in MySQL; large definition JSON must not enter its filesort buffer.
        var rows = selectPageRows(page);
        rows.sort(Comparator.comparing(SyncTaskDO::getId).reversed());
        return new PageResult<>(rows, total);
    }
}
