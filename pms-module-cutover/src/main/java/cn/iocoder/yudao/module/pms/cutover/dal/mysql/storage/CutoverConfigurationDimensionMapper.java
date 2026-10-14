package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverConfigurationDimensionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverConfigurationDimensionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 配置维度：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverConfigurationDimensionMapper extends BaseMapperX<CutoverConfigurationDimensionDO> {

    default List<CutoverConfigurationDimensionDO> selectListByParents(CutoverConfigurationDimensionListQuery query) {
        if (query == null || query.tenantId() == null || query.configurationRevisionIds() == null || query.configurationRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverConfigurationDimensionDO>()
                .eq(CutoverConfigurationDimensionDO::getTenantId, query.tenantId())
                .in(CutoverConfigurationDimensionDO::getConfigurationRevisionId, query.configurationRevisionIds())
                .orderByAsc(CutoverConfigurationDimensionDO::getSortOrder)
                .orderByAsc(CutoverConfigurationDimensionDO::getId));
    }
}
