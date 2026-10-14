package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanRiskMitigationDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverPlanRiskMitigationListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 方案逐项风险应对：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverPlanRiskMitigationMapper extends BaseMapperX<CutoverPlanRiskMitigationDO> {

    default List<CutoverPlanRiskMitigationDO> selectListByParents(CutoverPlanRiskMitigationListQuery query) {
        if (query == null || query.tenantId() == null || query.planRevisionIds() == null || query.planRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverPlanRiskMitigationDO>()
                .eq(CutoverPlanRiskMitigationDO::getTenantId, query.tenantId())
                .in(CutoverPlanRiskMitigationDO::getPlanRevisionId, query.planRevisionIds())
                .orderByAsc(CutoverPlanRiskMitigationDO::getSortOrder)
                .orderByAsc(CutoverPlanRiskMitigationDO::getId));
    }
}
