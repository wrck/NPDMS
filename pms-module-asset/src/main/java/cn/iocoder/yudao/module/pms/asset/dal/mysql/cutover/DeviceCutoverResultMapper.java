package cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.api.device.AssetCutoverResultApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.cutover.DeviceCutoverResultDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.cutover.query.DeviceCutoverScopeQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DeviceCutoverResultMapper extends BaseMapperX<DeviceCutoverResultDO> {
    DeviceDO selectCurrentDeviceForUpdate(@Param("query") DeviceCutoverScopeQuery query);

    default List<DeviceCutoverResultDO> byTask(AssetCutoverResultApi.Query query) {
        return selectList(new LambdaQueryWrapperX<DeviceCutoverResultDO>()
                .eq(DeviceCutoverResultDO::getTenantId, query.tenantId()).eq(DeviceCutoverResultDO::getProjectId, query.projectId())
                .eq(DeviceCutoverResultDO::getTaskId, query.taskId()).orderByAsc(DeviceCutoverResultDO::getDeviceId));
    }
}
