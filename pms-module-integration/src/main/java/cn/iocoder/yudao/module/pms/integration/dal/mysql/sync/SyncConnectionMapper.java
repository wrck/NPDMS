package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncConnectionDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
@Mapper
public interface SyncConnectionMapper extends BaseMapperX<SyncConnectionDO> {
    default SyncConnectionDO selectScoped(SyncQueries.Id query) {
        return selectOne(new LambdaQueryWrapperX<SyncConnectionDO>().eq(SyncConnectionDO::getTenantId,query.tenantId())
            .eq(SyncConnectionDO::getId,query.id()));
    }
    default PageResult<SyncConnectionDO> selectPage(SyncQueries.Page query) {
        return selectPage(query,new LambdaQueryWrapperX<SyncConnectionDO>().eq(SyncConnectionDO::getTenantId,query.getTenantId())
            .orderByDesc(SyncConnectionDO::getId));
    }
}

