package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage.CutoverReadinessSourceObjectDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query.CutoverReadinessSourceObjectListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** IMP来源业务对象集合：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverReadinessSourceObjectMapper extends BaseMapperX<CutoverReadinessSourceObjectDO> {

    default List<CutoverReadinessSourceObjectDO> selectListByParents(CutoverReadinessSourceObjectListQuery query) {
        if (query == null || query.tenantId() == null || query.sourceFactIds() == null || query.sourceFactIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverReadinessSourceObjectDO>()
                .eq(CutoverReadinessSourceObjectDO::getTenantId, query.tenantId())
                .in(CutoverReadinessSourceObjectDO::getSourceFactId, query.sourceFactIds())
                .orderByAsc(CutoverReadinessSourceObjectDO::getSortOrder)
                .orderByAsc(CutoverReadinessSourceObjectDO::getId));
    }
}
