package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverConfigurationPlanSectionScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverConfigurationPlanSectionScopeListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 章节适用集合：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverConfigurationPlanSectionScopeMapper extends BaseMapperX<CutoverConfigurationPlanSectionScopeDO> {

    default List<CutoverConfigurationPlanSectionScopeDO> selectListByParents(CutoverConfigurationPlanSectionScopeListQuery query) {
        if (query == null || query.tenantId() == null || query.planSectionIds() == null || query.planSectionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverConfigurationPlanSectionScopeDO>()
                .eq(CutoverConfigurationPlanSectionScopeDO::getTenantId, query.tenantId())
                .in(CutoverConfigurationPlanSectionScopeDO::getPlanSectionId, query.planSectionIds())
                .orderByAsc(CutoverConfigurationPlanSectionScopeDO::getSortOrder)
                .orderByAsc(CutoverConfigurationPlanSectionScopeDO::getId));
    }
}
