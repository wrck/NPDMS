package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage.CutoverReadinessDeviceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query.CutoverReadinessDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** IMP就绪冻结设备：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverReadinessDeviceMapper extends BaseMapperX<CutoverReadinessDeviceDO> {

    default List<CutoverReadinessDeviceDO> selectListByParents(CutoverReadinessDeviceListQuery query) {
        if (query == null || query.tenantId() == null || query.snapshotIds() == null || query.snapshotIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverReadinessDeviceDO>()
                .eq(CutoverReadinessDeviceDO::getTenantId, query.tenantId())
                .in(CutoverReadinessDeviceDO::getSnapshotId, query.snapshotIds())
                .orderByAsc(CutoverReadinessDeviceDO::getSortOrder)
                .orderByAsc(CutoverReadinessDeviceDO::getId));
    }
}
