package cn.iocoder.yudao.module.pms.asset.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.storage.DeviceCutoverResultFileDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.storage.query.DeviceCutoverResultFileListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** AST设备割接文件事实：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface DeviceCutoverResultFileMapper extends BaseMapperX<DeviceCutoverResultFileDO> {

    default List<DeviceCutoverResultFileDO> selectListByParents(DeviceCutoverResultFileListQuery query) {
        if (query == null || query.tenantId() == null || query.deviceCutoverResultIds() == null || query.deviceCutoverResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<DeviceCutoverResultFileDO>()
                .eq(DeviceCutoverResultFileDO::getTenantId, query.tenantId())
                .in(DeviceCutoverResultFileDO::getDeviceCutoverResultId, query.deviceCutoverResultIds())
                .orderByAsc(DeviceCutoverResultFileDO::getSortOrder)
                .orderByAsc(DeviceCutoverResultFileDO::getId));
    }
}
