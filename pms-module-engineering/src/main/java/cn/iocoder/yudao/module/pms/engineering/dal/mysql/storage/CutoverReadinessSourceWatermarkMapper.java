package cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.storage.CutoverReadinessSourceWatermarkDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.storage.query.CutoverReadinessSourceWatermarkListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** IMP来源水位集合：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverReadinessSourceWatermarkMapper extends BaseMapperX<CutoverReadinessSourceWatermarkDO> {

    default List<CutoverReadinessSourceWatermarkDO> selectListByParents(CutoverReadinessSourceWatermarkListQuery query) {
        if (query == null || query.tenantId() == null || query.sourceFactIds() == null || query.sourceFactIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverReadinessSourceWatermarkDO>()
                .eq(CutoverReadinessSourceWatermarkDO::getTenantId, query.tenantId())
                .in(CutoverReadinessSourceWatermarkDO::getSourceFactId, query.sourceFactIds())
                .orderByAsc(CutoverReadinessSourceWatermarkDO::getId));
    }
}
