package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface SyncRunMapper extends BaseMapperX<SyncRunDO> {
    List<SyncRunDO> selectMaintenance(SyncQueries.Due query);
    default SyncRunDO selectScoped(SyncQueries.Id query) {
        return selectOne(new LambdaQueryWrapperX<SyncRunDO>().eq(SyncRunDO::getTenantId,query.tenantId())
            .eq(SyncRunDO::getId,query.id()));
    }
    default SyncRunDO selectRequest(SyncQueries.Request query) {
        return selectOne(new LambdaQueryWrapperX<SyncRunDO>().eq(SyncRunDO::getTenantId,query.tenantId())
            .eq(SyncRunDO::getTaskId,query.taskId()).eq(SyncRunDO::getRequestKey,query.requestKey()));
    }
    default PageResult<SyncRunDO> selectPage(SyncQueries.Page query) {
        return selectPage(query,new LambdaQueryWrapperX<SyncRunDO>().eq(SyncRunDO::getTenantId,query.getTenantId())
            .eqIfPresent(SyncRunDO::getTaskId,query.getTaskId()).eqIfPresent(SyncRunDO::getParentRunId,query.getParentRunId())
            .isNotNull(query.getParentRunId()!=null,SyncRunDO::getPageNumber)
            .isNull(query.getParentRunId()==null,SyncRunDO::getPageNumber).orderByDesc(SyncRunDO::getId)
            .select(SyncRunDO.class,field -> !java.util.Set.of("resultJson","evidenceJson","configSnapshot")
                    .contains(field.getProperty())));
    }
}
