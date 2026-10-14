package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverConfigurationPlanSectionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverConfigurationPlanSectionListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 配置方案章节：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverConfigurationPlanSectionMapper extends BaseMapperX<CutoverConfigurationPlanSectionDO> {

    default List<CutoverConfigurationPlanSectionDO> selectListByParents(CutoverConfigurationPlanSectionListQuery query) {
        if (query == null || query.tenantId() == null || query.configurationRevisionIds() == null || query.configurationRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverConfigurationPlanSectionDO>()
                .eq(CutoverConfigurationPlanSectionDO::getTenantId, query.tenantId())
                .in(CutoverConfigurationPlanSectionDO::getConfigurationRevisionId, query.configurationRevisionIds())
                .orderByAsc(CutoverConfigurationPlanSectionDO::getSortOrder)
                .orderByAsc(CutoverConfigurationPlanSectionDO::getId));
    }
}
