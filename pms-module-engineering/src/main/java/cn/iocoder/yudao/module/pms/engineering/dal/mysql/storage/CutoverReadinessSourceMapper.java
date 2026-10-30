package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage.CutoverReadinessSourceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query.CutoverReadinessSourceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** IMP实施完成来源：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverReadinessSourceMapper extends BaseMapperX<CutoverReadinessSourceDO> {

    default List<CutoverReadinessSourceDO> selectListByParents(CutoverReadinessSourceListQuery query) {
        if (query == null || query.tenantId() == null || query.snapshotIds() == null || query.snapshotIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverReadinessSourceDO>()
                .eq(CutoverReadinessSourceDO::getTenantId, query.tenantId())
                .in(CutoverReadinessSourceDO::getSnapshotId, query.snapshotIds())
                .orderByAsc(CutoverReadinessSourceDO::getId));
    }
}
