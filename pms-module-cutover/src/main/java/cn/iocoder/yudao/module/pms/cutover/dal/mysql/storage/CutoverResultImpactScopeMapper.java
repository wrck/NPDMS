package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverResultImpactScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverResultImpactScopeListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 业务影响范围答案：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverResultImpactScopeMapper extends BaseMapperX<CutoverResultImpactScopeDO> {

    default List<CutoverResultImpactScopeDO> selectListByParents(CutoverResultImpactScopeListQuery query) {
        if (query == null || query.tenantId() == null || query.checklistResultIds() == null || query.checklistResultIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverResultImpactScopeDO>()
                .eq(CutoverResultImpactScopeDO::getTenantId, query.tenantId())
                .in(CutoverResultImpactScopeDO::getChecklistResultId, query.checklistResultIds())
                .orderByAsc(CutoverResultImpactScopeDO::getId));
    }
}
