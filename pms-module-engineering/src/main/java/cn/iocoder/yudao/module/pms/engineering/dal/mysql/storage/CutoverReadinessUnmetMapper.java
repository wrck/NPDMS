package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage.CutoverReadinessUnmetDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query.CutoverReadinessUnmetListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** IMP未满足原因集合：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverReadinessUnmetMapper extends BaseMapperX<CutoverReadinessUnmetDO> {

    default List<CutoverReadinessUnmetDO> selectListByParents(CutoverReadinessUnmetListQuery query) {
        if (query == null || query.tenantId() == null || query.snapshotIds() == null || query.snapshotIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverReadinessUnmetDO>()
                .eq(CutoverReadinessUnmetDO::getTenantId, query.tenantId())
                .in(CutoverReadinessUnmetDO::getSnapshotId, query.snapshotIds())
                .orderByAsc(CutoverReadinessUnmetDO::getSortOrder)
                .orderByAsc(CutoverReadinessUnmetDO::getId));
    }
}
