package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import org.apache.ibatis.annotations.Mapper;
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
    default PageResult<SyncTaskDO> selectPage(SyncQueries.Page query) {
        return selectPage(query,new LambdaQueryWrapperX<SyncTaskDO>().eq(SyncTaskDO::getTenantId,query.getTenantId())
            .orderByDesc(SyncTaskDO::getId));
    }
}
